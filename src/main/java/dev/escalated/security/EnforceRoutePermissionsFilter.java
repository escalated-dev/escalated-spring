package dev.escalated.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.services.TicketStatuses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Requires each staff route's permission while roles &amp; permissions is on (ADR
 * 2026-10-29-configurable-access-features). Escalated's port of Laravel's
 * {@code EnforceRoutePermissions} middleware.
 *
 * <p>Runs after the admin and agent gates ({@code AuthorizationFilter}), so both must pass. A staff
 * route missing from {@link RoutePermissions} is refused (fail closed); a user lacking a permission
 * gets 403. The status, bulk and reply routes decide part of what they need from the request body,
 * which is read here and replayed to the controller.
 *
 * <p>Runs inside Escalated's security filter chains only; its servlet registration is switched off
 * in {@link EscalatedSecurityConfig}.
 */
public class EnforceRoutePermissionsFilter extends OncePerRequestFilter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AccessControl access;
    private final ObjectProvider<TicketStatuses> statuses;

    public EnforceRoutePermissionsFilter(AccessControl access, ObjectProvider<TicketStatuses> statuses) {
        this.access = access;
        this.statuses = statuses;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!access.enforced() || !RoutePermissions.isStaffPath(path)) {
            chain.doFilter(request, response);
            return;
        }
        Authentication user = SecurityContextHolder.getContext().getAuthentication();
        List<String> mapped = RoutePermissions.mapped(request.getMethod(), path);
        if (mapped == null) {
            forbid(response);
            return;
        }

        HttpServletRequest forward = request;
        Map<String, Object> input = Map.of();
        if (decidedByBody(path, mapped)) {
            if (isJson(request)) {
                CachedBody cached = new CachedBody(request);
                forward = cached;
                input = cached.json();
            } else {
                input = parameters(request);
            }
        }

        List<String> required = RoutePermissions.required(request.getMethod(), path, input, this::solved);
        if (required == null || !access.allows(user, required)) {
            forbid(response);
            return;
        }
        chain.doFilter(forward, response);
    }

    private static boolean decidedByBody(String path, List<String> mapped) {
        String route = RoutePermissions.normalize(path);
        return mapped.contains("ticket.edit") || route.endsWith("/tickets/bulk") || route.endsWith("/replies");
    }

    private Boolean solved(String slug) {
        TicketStatuses known = statuses.getIfAvailable();
        if (known == null) {
            return false;
        }
        return known.custom(slug).map(status -> "solved".equals(status.getCategory())).orElse(false);
    }

    private static boolean isJson(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.toLowerCase(Locale.ROOT).contains("json");
    }

    private static Map<String, Object> parameters(HttpServletRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, given) -> values.put(key, given.length == 0 ? null : given[0]));
        return values;
    }

    static void forbid(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"This action is unauthorized.\"}");
    }

    /** The request with its body read once and served again to whoever reads it next. */
    static final class CachedBody extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBody(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        Map<String, Object> json() {
            try {
                Object parsed = JSON.readValue(body, Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    Map<String, Object> values = new LinkedHashMap<>();
                    map.forEach((key, value) -> values.put(String.valueOf(key), value));
                    return values;
                }
            } catch (IOException malformed) {
                // The controller reports a malformed body; nothing here depends on it.
            }
            return Map.of();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return stream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return stream.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return stream.read(buffer, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body),
                    encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }
    }
}
