package dev.escalated.ui;

import dev.escalated.config.EscalatedProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Answers an Inertia visit to one of Escalated's pages that ends in 403, 404, 419, 429, 500 or 503
 * with the shared frontend's {@code Escalated/Error} page, as Laravel and Rails do.
 *
 * <p>Without it the Inertia client gets an empty body, Boot's error JSON or Spring Security's
 * error back, and can only show it in a modal over the screen the user was on.
 *
 * <p>It sits in front of Spring Security, so it sees every way such a request ends: a status a
 * controller returned, a {@code sendError} from Security (a refused gate or CSRF token) or from
 * Spring MVC's exception resolvers, and an exception nothing handled (a 500, logged here). The
 * props are the ones Laravel and Rails send, {@code status} and a generic {@code message}; a
 * controller's or an exception's own text never reaches the page.
 *
 * <p>Left alone: requests without {@code X-Inertia} (full page loads, JSON and API clients),
 * Escalated's API, webhook, SSO, newsletter and WebSocket paths, every path outside
 * {@link UiRoutes#PREFIX}, responses that are already Inertia pages, and every other status
 * (redirects, validation's 303, the version check's 409). Registered only while
 * {@code escalated.ui.enabled} is on.
 */
public class InertiaErrorPageFilter extends OncePerRequestFilter {

    public static final String PAGE = "Escalated/Error";

    /** The statuses the page renders, and the generic message each one gets. */
    public static final Map<Integer, String> MESSAGES = Map.of(
            403, "You are not authorized to perform this action.",
            404, "The requested resource was not found.",
            419, "Your session has expired. Refresh the page and try again.",
            429, "Too many requests. Wait a moment and try again.",
            500, "Something went wrong on our end.",
            503, "The service is temporarily unavailable. Try again shortly.");

    /** Escalated paths that are not pages: their clients are not Inertia's. */
    private static final List<String> NOT_PAGES = List.of(
            "/api/", "/webhook", "/sso/", "/n/", "/ws/", "/widget");

    private static final Logger LOG = LoggerFactory.getLogger(InertiaErrorPageFilter.class);

    private final EscalatedProperties properties;

    public InertiaErrorPageFilter(EscalatedProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!Inertia.isInertia(request)) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals(UiRoutes.PREFIX) && !path.startsWith(UiRoutes.PREFIX + "/")) {
            return true;
        }
        String rest = path.substring(UiRoutes.PREFIX.length());
        return NOT_PAGES.stream().anyMatch(rest::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Captured captured = new Captured(response);

        try {
            chain.doFilter(request, captured);
        } catch (IOException | ServletException | RuntimeException exception) {
            if (response.isCommitted()) {
                throw exception;
            }
            LOG.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
            render(request, response, 500);
            return;
        }

        int status = captured.getStatus();
        if (MESSAGES.containsKey(status)
                && captured.getHeader(Inertia.HEADER) == null
                && !response.isCommitted()) {
            captured.resetBuffer();
            render(request, response, status);
            return;
        }

        if (captured.errorStatus != null) {
            // What was held back, as it would have gone: an error dispatch, the body discarded.
            if (captured.errorMessage == null) {
                response.sendError(captured.errorStatus);
            } else {
                response.sendError(captured.errorStatus, captured.errorMessage);
            }
            return;
        }
        captured.copyBodyToResponse();
    }

    private void render(HttpServletRequest request, HttpServletResponse response, int status) throws IOException {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("status", status);
        props.put("message", MESSAGES.get(status));

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("component", PAGE);
        page.put("props", props);
        page.put("url", Inertia.url(request));
        page.put("version", properties.getUi().getVersion());
        page.put("clearHistory", false);
        page.put("encryptHistory", false);

        byte[] body = Inertia.toJson(page).getBytes(StandardCharsets.UTF_8);
        response.resetBuffer();
        response.setStatus(status);
        response.setHeader(Inertia.HEADER, "true");
        response.setHeader(HttpHeaders.VARY, Inertia.HEADER);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.flushBuffer();
    }

    /** Buffers the body and holds a {@code sendError} back, so the page can still replace either. */
    private static final class Captured extends ContentCachingResponseWrapper {

        private Integer errorStatus;
        private String errorMessage;

        Captured(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void sendError(int status) {
            sendError(status, null);
        }

        @Override
        public void sendError(int status, String message) {
            errorStatus = status;
            errorMessage = message;
            setStatus(status);
        }
    }
}
