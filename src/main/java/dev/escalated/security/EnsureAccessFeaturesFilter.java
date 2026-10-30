package dev.escalated.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Answers 404 for the routes of an access feature whose switch is off (ADR
 * 2026-10-29-configurable-access-features), before any gate runs, as if the routes did not exist:
 * the same way a disabled widget's routes answer.
 *
 * <p>Runs inside Escalated's security filter chains only; its servlet registration is switched off
 * in {@link EscalatedSecurityConfig}.
 */
public class EnsureAccessFeaturesFilter extends OncePerRequestFilter {

    /** Each feature's routes. A path pattern as {@link RoutePermissions} reads it. */
    static final Map<String, List<String>> ROUTES = routes();

    private final AccessFeatures features;

    public EnsureAccessFeaturesFilter(AccessFeatures features) {
        this.features = features;
    }

    private static Map<String, List<String>> routes() {
        Map<String, List<String>> routes = new LinkedHashMap<>();
        routes.put(AccessFeatures.ROLES_PERMISSIONS, List.of(
                "/escalated/admin/roles", "/escalated/admin/roles/**",
                "/escalated/api/admin/roles", "/escalated/api/admin/roles/**"));
        routes.put(AccessFeatures.TWO_FACTOR, List.of(
                "/escalated/admin/settings/two-factor", "/escalated/admin/settings/two-factor/**",
                "/escalated/two-factor/**"));
        return routes;
    }

    /** The feature whose route {@code path} is, or null. */
    static String featureOf(String path) {
        String route = RoutePermissions.normalize(path);
        for (Map.Entry<String, List<String>> feature : ROUTES.entrySet()) {
            for (String pattern : feature.getValue()) {
                if (RoutePermissions.regex(pattern).matcher(route).matches()) {
                    return feature.getKey();
                }
            }
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String feature = featureOf(request.getRequestURI().substring(request.getContextPath().length()));
        if (feature != null && !features.enabled(feature)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Not Found\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
