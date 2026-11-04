package dev.escalated.ui;

import dev.escalated.services.ValidationFailedException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.EntityFilterException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * How a page's write fails, as Laravel's: invalid input goes back to the page it came from with
 * {@code errors} (a 303 the client follows, never a 422), a ticket out of reach is a 404, and an
 * assignment the access rules forbid is a 403. Malformed list filters on a GET from a JSON client
 * ({@code Accept: application/json}, no {@code X-Inertia}) are a 422 with the field errors, as
 * Laravel's {@code $request->validate()} answers a client that expects JSON.
 */
@ControllerAdvice(basePackages = "dev.escalated.ui.controllers")
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class UiExceptionHandler {

    private final Inertia inertia;

    public UiExceptionHandler(Inertia inertia) {
        this.inertia = inertia;
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<?> invalid(ValidationFailedException exception, HttpServletRequest request) {
        if ("GET".equals(request.getMethod()) && jsonClient(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", exception.getErrors().values().stream()
                    .flatMap(List::stream).findFirst().orElse("The given data was invalid."));
            body.put("errors", exception.getErrors());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
        }
        inertia.errors(exception.getErrors());
        return inertia.back(UiRoutes.PREFIX);
    }

    private static boolean jsonClient(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return request.getHeader("X-Inertia") == null && accept != null && accept.contains("application/json");
    }

    @ExceptionHandler({EntityNotFoundException.class, EntityFilterException.class})
    public ResponseEntity<Void> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Void> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
