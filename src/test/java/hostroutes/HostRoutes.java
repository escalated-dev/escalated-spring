package hostroutes;

import dev.escalated.security.EscalatedApiAuthenticator;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The parts of a host application that have nothing to do with support tickets:
 * an API of its own, secured its own way, and the callbacks behind Escalated's
 * JSON auth endpoints.
 *
 * <p>In a package of its own on purpose. Escalated component-scans
 * {@code dev.escalated}, so anything declared under it — a nested class in a
 * test included — is registered in every context the suite boots. Nothing scans
 * this package; only a test that imports it gets it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class HostRoutes {

    public static final String HOST_TOKEN = "host-issued-token";

    @Bean
    @Order(0)
    SecurityFilterChain hostApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/host/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    HostPingController hostPingController() {
        return new HostPingController();
    }

    @Bean
    EscalatedApiAuthenticator hostAuthenticator() {
        return new EscalatedApiAuthenticator() {
            @Override
            public Map<String, Object> validate(String token) {
                return HOST_TOKEN.equals(token) ? Map.of("email", "flutter@host.test") : null;
            }

            @Override
            public Map<String, Object> refresh(String token) {
                return HOST_TOKEN.equals(token) ? Map.of("token", "refreshed") : null;
            }
        };
    }
}
