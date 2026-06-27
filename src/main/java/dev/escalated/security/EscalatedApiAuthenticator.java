package dev.escalated.security;

import java.util.Map;

/**
 * Host-app authentication callbacks for the general JSON API
 * ({@code /escalated/api/v1/auth/*}) consumed by the Flutter app. Provide a
 * Spring {@code @Bean} implementing the methods your app needs — Escalated owns
 * no credentials or sessions, so it ships no password-hashing dependency.
 *
 * <p>Each method returns the JSON payload to send (e.g. token + user) on
 * success, or {@code null} for an authentication failure (401). An
 * unimplemented method (the default throws {@link UnsupportedOperationException})
 * — or no bean at all — makes its endpoint respond {@code 501}.
 */
public interface EscalatedApiAuthenticator {

    /** Authenticate a login request (email/password etc.). */
    default Map<String, Object> authenticate(Map<String, Object> params) {
        throw new UnsupportedOperationException();
    }

    /** Register a new account. */
    default Map<String, Object> register(Map<String, Object> params) {
        throw new UnsupportedOperationException();
    }

    /** Validate a token and return the associated user. */
    default Map<String, Object> validate(String token) {
        throw new UnsupportedOperationException();
    }

    /** Exchange/refresh a token. */
    default Map<String, Object> refresh(String token) {
        throw new UnsupportedOperationException();
    }

    /** Update the authenticated user's profile. */
    default Map<String, Object> updateProfile(String token, Map<String, Object> attrs) {
        throw new UnsupportedOperationException();
    }

    /** Invalidate a token (best-effort; default no-op). */
    default void logout(String token) {
        // No-op by default.
    }
}
