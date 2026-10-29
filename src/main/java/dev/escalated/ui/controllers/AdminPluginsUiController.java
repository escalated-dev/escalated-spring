package dev.escalated.ui.controllers;

import dev.escalated.plugins.PluginAdmin;
import dev.escalated.plugins.PluginRegistry;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Admin &gt; Plugins and the plugins' own pages, as the plugin contract has them. Admins only (the
 * web security chain).
 *
 * <ul>
 *   <li>{@code GET /admin/plugins}: {@code Escalated/Admin/Plugins/Index} with {@code plugins} and
 *       {@code can_upload: false} (plugins are installed by the deployer, so there is no upload
 *       route).
 *   <li>{@code POST /admin/plugins/{slug}/activate}, {@code POST .../deactivate} and
 *       {@code DELETE /admin/plugins/{slug}}: a 303 to the index with a {@code success} or
 *       {@code error} flash. An unknown slug is an error flash, or a 404 for a JSON request.
 *   <li>{@code GET /admin/plugins/{slug}/{route}}: an active plugin's page, rendered as
 *       {@code Escalated/Plugin/Page}, behind the page's capability (403).
 * </ul>
 *
 * <p>One handler routes them all, so that any other method or path is a 404 rather than a 405.
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class AdminPluginsUiController {

    static final String INDEX_PAGE = "Escalated/Admin/Plugins/Index";
    static final String PLUGIN_PAGE = "Escalated/Plugin/Page";

    private static final String BASE = UiRoutes.PREFIX + "/admin/plugins";
    private static final Logger LOG = LoggerFactory.getLogger(AdminPluginsUiController.class);

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final PluginRegistry registry;
    private final UiRoutes routes;

    public AdminPluginsUiController(EscalatedUiRenderer renderer, Inertia inertia, PluginRegistry registry,
                                    UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.registry = registry;
        this.routes = routes;
    }

    @RequestMapping({BASE, BASE + "/**"})
    public Object handle(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        boolean read = "GET".equals(method) || "HEAD".equals(method);
        String rest = path.length() > BASE.length() ? path.substring(BASE.length()) : "";
        if (rest.isEmpty() || "/".equals(rest)) {
            return read ? index() : notFound();
        }
        rest = rest.substring(1);
        int slash = rest.indexOf('/');
        String slug = decode(slash < 0 ? rest : rest.substring(0, slash));
        String below = slash < 0 ? "" : rest.substring(slash + 1);
        Authentication actor = SecurityContextHolder.getContext().getAuthentication();
        if (below.isEmpty() && "DELETE".equals(method)) {
            return lifecycle(request, slug, PluginAdmin.Action.UNINSTALL, actor);
        }
        if ("activate".equals(below) && "POST".equals(method)) {
            return lifecycle(request, slug, PluginAdmin.Action.ACTIVATE, actor);
        }
        if ("deactivate".equals(below) && "POST".equals(method)) {
            return lifecycle(request, slug, PluginAdmin.Action.DEACTIVATE, actor);
        }
        if (!below.isEmpty() && read) {
            return page(slug, decode(below), actor);
        }
        return notFound();
    }

    private Object index() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("plugins", registry.list());
        props.put("can_upload", false);
        return renderer.render(INDEX_PAGE, props);
    }

    private Object lifecycle(HttpServletRequest request, String slug, PluginAdmin.Action action, Authentication actor) {
        PluginAdmin.Outcome outcome = PluginAdmin.apply(registry, slug, action, actor);
        if (outcome.unknown() && wantsJson(request)) {
            return notFound();
        }
        if (outcome.failure() != null) {
            LOG.warn("Escalated plugin {} {} failed: {}", action, slug, outcome.failure().toString());
        }
        inertia.flash(outcome.success() ? "success" : "error", outcome.message());
        return inertia.redirect(routes.url("escalated.admin.plugins.index"));
    }

    private Object page(String slug, String route, Authentication actor) {
        PluginAdmin.Page page = PluginAdmin.page(registry, slug, route, actor);
        if (page == null) {
            return notFound();
        }
        if (page.forbidden()) {
            return ResponseEntity.status(403).build();
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("plugin", page.plugin());
        props.put("component", page.component());
        props.put("layout", page.layout());
        props.put("props", page.props());
        return renderer.render(PLUGIN_PAGE, props);
    }

    private static boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return !Inertia.isInertia(request) && accept != null && accept.contains("application/json");
    }

    private static ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
