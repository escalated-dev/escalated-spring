package dev.escalated.ui;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Test helpers for the Inertia pages. */
final class UiTestSupport {

    private UiTestSupport() {}

    /**
     * The CSRF token as Inertia's client sends it: the {@code XSRF-TOKEN} cookie echoed in
     * {@code X-XSRF-TOKEN}. Used instead of spring-security-test's {@code csrf()}, which swaps the
     * filter's token repository for the rest of the cached context.
     */
    static RequestPostProcessor xsrf() {
        return request -> {
            String token = UUID.randomUUID().toString();
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }
}
