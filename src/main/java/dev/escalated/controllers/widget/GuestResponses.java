package dev.escalated.controllers.widget;

import java.util.Map;
import org.springframework.http.ResponseEntity;

/**
 * Guest answers carry credentials or private correspondence: never cached,
 * never leaked through a Referer.
 */
final class GuestResponses {

    private GuestResponses() {
    }

    static <T> ResponseEntity<T> of(int status, T body) {
        return ResponseEntity.status(status)
                .header("Cache-Control", "no-store, private")
                .header("Referrer-Policy", "no-referrer")
                .body(body);
    }

    static ResponseEntity<Map<String, String>> invalidToken() {
        // As the NestJS reference's GuestAccessGuard answers.
        return of(403, Map.of("error", "Invalid guest access token"));
    }
}
