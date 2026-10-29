package dev.escalated.plugins;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Plugin endpoints and webhooks.
 *
 * <ul>
 *   <li>{@code /escalated/api/plugins/{slug}{path}}: an active plugin's endpoint, behind the admin
 *       guard (see {@code EscalatedSecurityConfig}) and the endpoint's capability. A call that
 *       changes state must come from a page script ({@code X-Requested-With} or {@code X-Inertia}),
 *       which another site cannot send without a CORS preflight.
 *   <li>{@code /escalated/webhooks/plugins/{slug}{path}}: an active plugin's webhook, unguarded. The
 *       plugin authenticates the sender.
 * </ul>
 *
 * <p>Anything else, and any route of an inactive plugin, is a 404.
 */
@Controller
public class PluginEndpointController {

    static final String ENDPOINTS = "/escalated/api/plugins";
    static final String WEBHOOKS = "/escalated/webhooks/plugins";

    private static final Logger LOG = LoggerFactory.getLogger(PluginEndpointController.class);

    private final PluginRegistry registry;

    public PluginEndpointController(PluginRegistry registry) {
        this.registry = registry;
    }

    @RequestMapping({ENDPOINTS + "/{slug}", ENDPOINTS + "/{slug}/**"})
    public void endpoint(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, ENDPOINTS, false);
    }

    @RequestMapping({WEBHOOKS + "/{slug}", WEBHOOKS + "/{slug}/**"})
    public void webhook(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, WEBHOOKS, true);
    }

    private void serve(HttpServletRequest request, HttpServletResponse response, String base, boolean webhook)
            throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(base + "/")) {
            response.sendError(404);
            return;
        }
        String rest = path.substring(base.length() + 1);
        int slash = rest.indexOf('/');
        String slug = URLDecoder.decode(slash < 0 ? rest : rest.substring(0, slash), StandardCharsets.UTF_8);
        String below = slash < 0 ? "/" : rest.substring(slash);

        PluginEntry entry = registry.active(slug);
        List<ParsedRoute> routes = entry == null ? List.of() : webhook ? entry.webhooks : entry.endpoints;
        ParsedRoute.Match match = ParsedRoute.find(routes, request.getMethod(), below);
        if (entry == null || match == null) {
            message(response, 404, "Not Found");
            return;
        }
        Authentication actor = webhook ? null : SecurityContextHolder.getContext().getAuthentication();
        if (!webhook) {
            if (!registry.allows(actor, match.route().capability())) {
                message(response, 403, "Forbidden");
                return;
            }
            String method = request.getMethod();
            boolean safe = "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
            if (!safe && request.getHeader("X-Requested-With") == null && request.getHeader("X-Inertia") == null) {
                message(response, 403, "Plugin endpoint calls must be sent by a page script.");
                return;
            }
        }

        PluginRequest pluginRequest;
        try {
            pluginRequest = PluginHttp.request(request, match.route().path(), match.params());
        } catch (PluginHttp.TooLargeException ex) {
            message(response, 413, "Request Entity Too Large");
            return;
        }
        Object result;
        try {
            result = registry.invoke(entry, match.route(), pluginRequest, webhook, actor);
        } catch (PluginRegistry.RuntimeUnavailableException ex) {
            message(response, 503, "The plugin runtime is not available.");
            return;
        } catch (Exception ex) {
            // The client gets a generic answer; the cause goes to the log.
            LOG.warn("Escalated plugin {} {} {} failed: {}", entry.name, match.route().method(), match.route().path(),
                    ex.toString());
            message(response, 500, "The plugin could not handle this request.");
            return;
        }
        PluginHttp.Reply reply;
        try {
            reply = PluginHttp.reply(result);
        } catch (PluginHttp.InvalidResponseException ex) {
            LOG.warn("Escalated plugin {} {} {}: {}", entry.name, match.route().method(), match.route().path(),
                    ex.getMessage());
            message(response, 502, ex.getMessage());
            return;
        }
        response.setStatus(reply.status());
        for (Map.Entry<String, String> header : reply.headers().entrySet()) {
            response.setHeader(header.getKey(), header.getValue());
        }
        if (!"HEAD".equals(request.getMethod())) {
            response.getOutputStream().write(reply.body());
        }
    }

    private static void message(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(PluginJson.write(Map.of("message", message)).getBytes(StandardCharsets.UTF_8));
    }
}
