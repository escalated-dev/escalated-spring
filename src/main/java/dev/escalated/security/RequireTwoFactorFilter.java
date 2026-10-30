package dev.escalated.security;

import dev.escalated.services.TwoFactorService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The two-factor challenge between the host's login and the admin and agent pages (ADR
 * 2026-10-29-configurable-access-features), Laravel's {@code RequireTwoFactor}. While two-factor is
 * on, after the gates:
 *
 * <ul>
 *   <li>a user with a confirmed enrolment who has not passed the challenge in this session is sent to
 *       {@code /escalated/two-factor/challenge};
 *   <li>while two-factor is required, a staff user without one is sent to enrol and reaches nothing
 *       but the enrolment routes.
 * </ul>
 *
 * <p>A browser or Inertia visit is redirected (and a GET remembered, to return to); a JSON client
 * gets 403 with {@code two_factor_required: true}. API token requests are not session-based and are
 * not challenged. Runs inside Escalated's web security chain only.
 */
public class RequireTwoFactorFilter extends OncePerRequestFilter {

    public static final String CHALLENGE = "/escalated/two-factor/challenge";
    public static final String ENROLMENT = "/escalated/admin/settings/two-factor";

    private final TwoFactorService twoFactor;

    public RequireTwoFactorFilter(TwoFactorService twoFactor) {
        this.twoFactor = twoFactor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !(path.equals("/escalated/admin") || path.startsWith("/escalated/admin/")
                || path.equals("/escalated/agent") || path.startsWith("/escalated/agent/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication user = SecurityContextHolder.getContext().getAuthentication();
        if (!twoFactor.enabled() || user == null || !user.isAuthenticated()
                || user instanceof AnonymousAuthenticationToken
                || user.getAuthorities().stream()
                        .anyMatch(a -> EscalatedAuthorization.API_TOKEN_AUTHORITY.equals(a.getAuthority()))) {
            chain.doFilter(request, response);
            return;
        }
        if (twoFactor.confirmedFor(user.getName())) {
            if (TwoFactorSession.passed(request, user.getName())) {
                chain.doFilter(request, response);
                return;
            }
            deny(request, response, CHALLENGE, "Two-factor authentication is required.");
            return;
        }
        String path = path(request);
        boolean enrolment = path.equals(ENROLMENT) || path.startsWith(ENROLMENT + "/");
        if (!enrolment && twoFactor.required()) {
            deny(request, response, ENROLMENT, "Set up two-factor authentication to continue.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String path(HttpServletRequest request) {
        return RoutePermissions.normalize(request.getRequestURI().substring(request.getContextPath().length()));
    }

    private static void deny(HttpServletRequest request, HttpServletResponse response, String location, String message)
            throws IOException {
        String accept = request.getHeader("Accept");
        boolean json = request.getHeader("X-Inertia") == null && accept != null
                && accept.toLowerCase(Locale.ROOT).contains("application/json");
        if (json) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"" + message + "\",\"two_factor_required\":true}");
            return;
        }
        boolean get = "GET".equalsIgnoreCase(request.getMethod());
        if (get) {
            String query = request.getQueryString();
            TwoFactorSession.rememberIntended(request, request.getRequestURI()
                    + (query == null || query.isEmpty() ? "" : "?" + query));
        }
        response.setStatus(get ? HttpServletResponse.SC_FOUND : HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", request.getContextPath() + location);
    }
}
