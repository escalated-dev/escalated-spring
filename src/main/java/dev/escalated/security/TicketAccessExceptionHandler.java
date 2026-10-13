package dev.escalated.security;

import jakarta.persistence.EntityNotFoundException;

import org.hibernate.EntityFilterException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "dev.escalated.controllers")
public class TicketAccessExceptionHandler {
    @ExceptionHandler({EntityNotFoundException.class, EntityFilterException.class})
    public ResponseEntity<Map<String, String>> notFound(RuntimeException exception) {
        return ResponseEntity.status(404).body(Map.of("error", "Resource not found"));
    }
}
