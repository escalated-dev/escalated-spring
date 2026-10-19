package dev.escalated.widget;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CORS for the widget routes ({@code /escalated/api/widget/**}, chat
 * included). Only origins {@link WidgetOrigins} allows get CORS headers;
 * everything else passes through untouched.
 *
 * <ul>
 *   <li>A preflight ({@code OPTIONS} with {@code Access-Control-Request-Method})
 *       is answered here with 204. Allowed origins get the grant, others none.</li>
 *   <li>Any other request from an allowed origin gets
 *       {@code Access-Control-Allow-Origin} before the rest of the chain runs,
 *       so every answer carries it: the 401 from the security chain, the 429
 *       from the guest rate limit, and the 4xx a controller returns.</li>
 *   <li>Such a request is always a guest. Its cookies, session and principal
 *       are hidden from everything after this filter, and any security context
 *       set before it is cleared for the request, so it never acts as whoever
 *       is signed in to the host in the same browser.</li>
 * </ul>
 *
 * <p>Credentials are never allowed, so browsers send no cookies with these
 * requests anyway. Guest tokens travel in the path or the
 * {@code Authorization} header, which is left as it is.
 *
 * <p>Registered ahead of Spring Security by {@link WidgetEmbedConfiguration}.
 * Spring's own {@code CorsConfiguration} is deliberately not used: it would
 * apply the host's global CORS mappings (and credentials) to these routes.
 */
public class WidgetCorsFilter extends OncePerRequestFilter {

    public static final String PATH = "/escalated/api/widget";
    public static final String ALLOW_METHODS = "GET, POST, OPTIONS";
    public static final String ALLOW_HEADERS = "Accept, Authorization, Content-Type, X-Requested-With";
    public static final int MAX_AGE = 600;

    private final WidgetOrigins origins;

    public WidgetCorsFilter(WidgetOrigins origins) {
        this.origins = origins;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean widget = path.equals(PATH) || path.startsWith(PATH + "/");
        // embed.js is public to every site and answers with its own headers.
        return !widget || path.equals(PATH + "/embed.js");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = WidgetOrigins.crossOrigin(request);
        boolean allowed = origin != null && origins.allows(origin);

        if ("OPTIONS".equals(request.getMethod()) && request.getHeader("Access-Control-Request-Method") != null) {
            varyOnOrigin(response);
            if (allowed) {
                grant(response, origin);
                response.setHeader("Access-Control-Allow-Methods", ALLOW_METHODS);
                response.setHeader("Access-Control-Allow-Headers", ALLOW_HEADERS);
                response.setHeader("Access-Control-Max-Age", String.valueOf(MAX_AGE));
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        if (!allowed) {
            chain.doFilter(request, response);
            return;
        }

        request.setAttribute(WidgetOrigins.ATTRIBUTE, origin);
        grant(response, origin);

        // A cross-site embed is always anonymous.
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContextHolder.clearContext();
        try {
            chain.doFilter(new GuestRequest(request), response);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private static void grant(HttpServletResponse response, String origin) {
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Expose-Headers", "Retry-After");
        varyOnOrigin(response);
    }

    /** Add {@code Origin} to the response's Vary header, keeping whatever it already lists. */
    private static void varyOnOrigin(HttpServletResponse response) {
        Set<String> values = new LinkedHashSet<>();
        for (String header : response.getHeaders("Vary")) {
            for (String value : header.split(",")) {
                if (!value.isBlank()) {
                    values.add(value.trim());
                }
            }
        }
        if (values.contains("*") || values.stream().anyMatch("Origin"::equalsIgnoreCase)) {
            return;
        }
        values.add("Origin");
        response.setHeader("Vary", String.join(", ", values));
    }

    /**
     * The request as everything after this filter sees it: no cookies, no
     * session, no principal.
     */
    static final class GuestRequest extends HttpServletRequestWrapper {

        GuestRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public jakarta.servlet.http.Cookie[] getCookies() {
            return null;
        }

        @Override
        public String getHeader(String name) {
            return isCookie(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return isCookie(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            for (String name : Collections.list(super.getHeaderNames())) {
                if (!isCookie(name)) {
                    names.add(name);
                }
            }
            return Collections.enumeration(names);
        }

        @Override
        public HttpSession getSession(boolean create) {
            if (!create) {
                return null;
            }
            // Handing out the browser's session would hand out its sign-in.
            throw new IllegalStateException("A cross-site widget request has no session");
        }

        @Override
        public HttpSession getSession() {
            return getSession(true);
        }

        @Override
        public String getRequestedSessionId() {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public String changeSessionId() {
            throw new IllegalStateException("A cross-site widget request has no session");
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public String getRemoteUser() {
            return null;
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
        }

        @Override
        public String getAuthType() {
            return null;
        }

        private static boolean isCookie(String name) {
            return "Cookie".equalsIgnoreCase(name);
        }
    }
}
