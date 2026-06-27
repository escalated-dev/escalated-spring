package dev.escalated.controllers.api;

import dev.escalated.security.EscalatedApiAuthenticator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * General JSON API authentication for the Flutter app and integrations. All
 * credential handling is delegated to a host-provided
 * {@link EscalatedApiAuthenticator} bean — Escalated owns no passwords or
 * sessions. No bean (or an unimplemented method) responds 501; a method
 * returning {@code null} is an authentication failure (401).
 *
 * <p>login/register/refresh/logout are permitted without a token (see
 * EscalatedSecurityConfig); me/profile use the bearer token.
 */
@RestController
@RequestMapping("/escalated/api/v1/auth")
public class ApiAuthController {

    private final EscalatedApiAuthenticator authenticator;

    public ApiAuthController(Optional<EscalatedApiAuthenticator> authenticator) {
        this.authenticator = authenticator.orElse(null);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) Map<String, Object> body) {
        return delegate(() -> authenticator.authenticate(body == null ? Map.of() : body));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody(required = false) Map<String, Object> body) {
        return delegate(() -> authenticator.register(body == null ? Map.of() : body));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader(value = "Authorization", required = false) String auth) {
        return delegate(() -> authenticator.refresh(bearer(auth)));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        return delegate(() -> authenticator.validate(bearer(auth)));
    }

    @PatchMapping("/profile")
    public ResponseEntity<?> profile(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody(required = false) Map<String, Object> body) {
        return delegate(() -> authenticator.updateProfile(bearer(auth), body == null ? Map.of() : body));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (authenticator != null) {
            try {
                authenticator.logout(bearer(auth));
            } catch (UnsupportedOperationException ignored) {
                // Best-effort: logout always succeeds.
            }
        }
        return ResponseEntity.ok(Map.of("data", Map.of("success", true)));
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody(required = false) Map<String, Object> body) {
        Object token = body == null ? null : body.get("token");
        if (token == null || token.toString().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "token is required"));
        }
        if (authenticator == null) {
            return notConfigured();
        }
        try {
            Map<String, Object> user = authenticator.validate(token.toString());
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
            }
            return ResponseEntity.ok(Map.of("valid", true, "user", user));
        } catch (UnsupportedOperationException e) {
            return notConfigured();
        }
    }

    private ResponseEntity<?> delegate(Supplier<Map<String, Object>> call) {
        if (authenticator == null) {
            return notConfigured();
        }
        try {
            Map<String, Object> result = call.get();
            if (result == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
            }
            return ResponseEntity.ok(Map.of("data", result));
        } catch (UnsupportedOperationException e) {
            return notConfigured();
        }
    }

    private ResponseEntity<?> notConfigured() {
        return ResponseEntity.status(501).body(Map.of("error", "Authentication is not configured"));
    }

    private String bearer(String header) {
        if (header == null) {
            return "";
        }
        return header.startsWith("Bearer ") ? header.substring(7).trim() : header;
    }
}
