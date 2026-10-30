package errorprobe;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stand-in routes for failures the suite cannot otherwise provoke on a real Escalated page, and a
 * host route of the same shape. In a package of its own so only the test that imports it gets it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ErrorProbe {

    @Bean
    @Order(0)
    SecurityFilterChain errorProbeHostSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/host/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /** Registered as a member of this configuration. */
    @RestController
    public static class ErrorProbeController {

        @GetMapping("/escalated/error-probe/419")
        public ResponseEntity<String> sessionExpired() {
            return ResponseEntity.status(419).body("upstream detail");
        }

        @GetMapping("/escalated/error-probe/429")
        public ResponseEntity<String> tooMany() {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("upstream detail");
        }

        @GetMapping("/escalated/error-probe/500")
        public String boom() {
            throw new IllegalStateException("relation escalated_secret_table does not exist");
        }

        @GetMapping("/escalated/error-probe/503")
        public String down() {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "upstream detail");
        }

        @GetMapping("/escalated/error-probe/redirect")
        public ResponseEntity<Void> redirect() {
            return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/escalated/agent").build();
        }

        @GetMapping("/escalated/api/error-probe")
        public ResponseEntity<String> apiMissing() {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"message\":\"api detail\"}");
        }

        @GetMapping("/host/error-probe")
        public ResponseEntity<String> hostMissing() {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("host page");
        }
    }
}
