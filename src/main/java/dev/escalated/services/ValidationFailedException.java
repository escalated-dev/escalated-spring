package dev.escalated.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Invalid input to an admin write, answered as 422 with Laravel's shape:
 * {@code {"message": ..., "errors": {"field": ["..."]}}}.
 */
public class ValidationFailedException extends RuntimeException {

    private final Map<String, List<String>> errors;

    public ValidationFailedException(Map<String, List<String>> errors) {
        super(errors.values().stream().flatMap(List::stream).findFirst().orElse("The given data was invalid."));
        this.errors = new LinkedHashMap<>(errors);
    }

    public Map<String, List<String>> getErrors() {
        return errors;
    }
}
