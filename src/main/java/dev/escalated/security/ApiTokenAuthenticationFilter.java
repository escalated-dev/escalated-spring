package dev.escalated.security;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.ApiToken;
import dev.escalated.services.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates Escalated API tokens on {@code /escalated/api/**}.
 *
 * <p>Runs only inside Escalated's API filter chain. It is a bean so that chain
 * can be given it, but its servlet registration is switched off in
 * {@link EscalatedSecurityConfig}: a filter bean Boot registers on its own runs
 * on every request, and would answer the host's own {@code Bearer} routes with
 * 401 because their tokens are not Escalated's.
 */
@Component
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    /**
     * The JSON auth endpoints receive tokens the <em>host</em> issued, and pass
     * them to the host's {@link EscalatedApiAuthenticator}. They are not
     * Escalated API tokens, so this filter leaves them alone.
     */
    static final String HOST_AUTH_PATH = "/escalated/api/v1/auth/";

    private final ApiTokenService apiTokenService;

    public ApiTokenAuthenticationFilter(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith(HOST_AUTH_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            AgentProfile agent;
            try {
                ApiToken apiToken = apiTokenService.validateToken(authHeader.substring(7));
                agent = apiToken.getAgent();
            } catch (RuntimeException ex) {
                agent = null;
            }

            if (agent == null || !agent.isActive()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\": \"Invalid or expired token\"}");
                return;
            }

            UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(
                    agent.getEmail(),
                    null,
                    EscalatedAuthorization.authoritiesOf(agent));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
