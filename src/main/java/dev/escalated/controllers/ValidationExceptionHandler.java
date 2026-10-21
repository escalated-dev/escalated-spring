package dev.escalated.controllers;

import dev.escalated.services.ValidationFailedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Answers {@link ValidationFailedException} as 422 {@code {message, errors}}. */
@RestControllerAdvice(basePackages = "dev.escalated.controllers")
public class ValidationExceptionHandler {

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<Map<String, Object>> invalid(ValidationFailedException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", exception.getMessage());
        body.put("errors", exception.getErrors());
        return ResponseEntity.status(422).body(body);
    }

    @ExceptionHandler(dev.escalated.services.GuestBudgetExceededException.class)
    public ResponseEntity<Map<String, Object>> tooMany(dev.escalated.services.GuestBudgetExceededException exception) {
        return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(exception.getRetryAfterSeconds()))
                .header("Cache-Control", "no-store, private")
                .body(Map.of("message", exception.getMessage()));
    }
}
