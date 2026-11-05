package embedhost;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A host with an admin panel of its own at {@code /admin}, embedding Escalated's admin under
 * {@code /admin/support} as {@code docs/embedding-admin.md} sets it up: its own security chain for
 * {@code /admin/**} (signed in; CSRF left to Escalated's chain on the embedded pages; a CSP of its
 * own), and a middleware filter, {@code hostAdminOnly}, named in
 * {@code escalated.ui.embed.middleware}.
 *
 * <p>In a package of its own so that only the embed tests that import it get it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EmbedHost {

    /** Set by the host middleware on every request it lets through. */
    public static final String MIDDLEWARE_HEADER = "X-Host-Middleware";

    /** The host middleware refuses users whose name starts with this. */
    public static final String BLOCKED = "blocked-";

    public static final String HOST_CSP = "default-src 'self'";

    @Bean
    @Order(5)
    SecurityFilterChain hostAdminSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/admin/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers("/admin/support/**"))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(HOST_CSP)))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    /** The host's own check: who may use its admin panel. */
    @Bean
    Filter hostAdminOnly() {
        return (request, response, chain) -> {
            HttpServletResponse http = (HttpServletResponse) response;
            Authentication user = SecurityContextHolder.getContext().getAuthentication();
            if (user == null || user.getName() == null || user.getName().startsWith(BLOCKED)) {
                http.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            http.setHeader(MIDDLEWARE_HEADER, "ran " + ((HttpServletRequest) request).getRequestURI());
            chain.doFilter(request, response);
        };
    }

    /** Runs only where Escalated runs it, not on every request. */
    @Bean
    FilterRegistrationBean<Filter> hostAdminOnlyRegistration(Filter hostAdminOnly) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(hostAdminOnly);
        registration.setEnabled(false);
        return registration;
    }
}
