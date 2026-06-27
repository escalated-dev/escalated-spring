package dev.escalated.controllers.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.escalated.security.EscalatedApiAuthenticator;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ApiAuthControllerTest {

    @Test
    void loginReturns501WhenNoBean() {
        ApiAuthController ctrl = new ApiAuthController(Optional.empty());
        assertEquals(501, ctrl.login(Map.of()).getStatusCode().value());
    }

    @Test
    void loginDelegatesToAuthenticator() {
        EscalatedApiAuthenticator auth = new EscalatedApiAuthenticator() {
            @Override
            public Map<String, Object> authenticate(Map<String, Object> params) {
                return Map.of("token", "abc", "email", params.get("email"));
            }
        };
        ApiAuthController ctrl = new ApiAuthController(Optional.of(auth));

        ResponseEntity<?> res = ctrl.login(Map.of("email", "a@b.com"));

        assertEquals(200, res.getStatusCode().value());
        assertEquals(Map.of("data", Map.of("token", "abc", "email", "a@b.com")), res.getBody());
    }

    @Test
    void loginReturns401WhenAuthenticatorReturnsNull() {
        EscalatedApiAuthenticator auth = new EscalatedApiAuthenticator() {
            @Override
            public Map<String, Object> authenticate(Map<String, Object> params) {
                return null;
            }
        };
        ApiAuthController ctrl = new ApiAuthController(Optional.of(auth));

        assertEquals(401, ctrl.login(Map.of()).getStatusCode().value());
    }

    @Test
    void unimplementedMethodReturns501() {
        EscalatedApiAuthenticator auth = new EscalatedApiAuthenticator() {
            @Override
            public Map<String, Object> authenticate(Map<String, Object> params) {
                return Map.of("ok", true);
            }
        };
        ApiAuthController ctrl = new ApiAuthController(Optional.of(auth));

        assertEquals(501, ctrl.register(Map.of()).getStatusCode().value());
    }

    @Test
    void meForwardsBearerToken() {
        final String[] seen = {null};
        EscalatedApiAuthenticator auth = new EscalatedApiAuthenticator() {
            @Override
            public Map<String, Object> validate(String token) {
                seen[0] = token;
                return Map.of("id", 7);
            }
        };
        ApiAuthController ctrl = new ApiAuthController(Optional.of(auth));

        ResponseEntity<?> res = ctrl.me("Bearer tok123");

        assertEquals(200, res.getStatusCode().value());
        assertEquals("tok123", seen[0]);
    }

    @Test
    void logoutAlwaysSucceeds() {
        ApiAuthController ctrl = new ApiAuthController(Optional.empty());

        ResponseEntity<?> res = ctrl.logout("Bearer x");

        assertEquals(200, res.getStatusCode().value());
        assertEquals(Map.of("data", Map.of("success", true)), res.getBody());
    }

    @Test
    void validateRequiresToken() {
        ApiAuthController ctrl = new ApiAuthController(Optional.of(new EscalatedApiAuthenticator() {}));

        assertEquals(400, ctrl.validate(Map.of()).getStatusCode().value());
    }
}
