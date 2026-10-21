package dev.escalated.ui;

import dev.escalated.services.ValidationFailedException;
import jakarta.persistence.EntityNotFoundException;
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
 * assignment the access rules forbid is a 403.
 */
@ControllerAdvice(basePackages = "dev.escalated.ui.controllers")
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class UiExceptionHandler {

    private final Inertia inertia;

    public UiExceptionHandler(Inertia inertia) {
        this.inertia = inertia;
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<Void> invalid(ValidationFailedException exception) {
        inertia.errors(exception.getErrors());
        return inertia.back(UiRoutes.PREFIX);
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
