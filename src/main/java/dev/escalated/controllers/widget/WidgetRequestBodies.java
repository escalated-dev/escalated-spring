package dev.escalated.controllers.widget;

import dev.escalated.models.Department;
import dev.escalated.services.DepartmentService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads the text fields of a widget request body that also carries a
 * {@code metadata} object. Scalars read as text, as they did when the body
 * was bound to {@code Map<String, String>}; an object or array where text
 * belongs is still a 400.
 */
final class WidgetRequestBodies {

    private WidgetRequestBodies() {
    }

    static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be text");
    }

    /**
     * The id of the active department a widget request names, or {@code null}
     * when it names none, an unknown one or an inactive one: the ticket or
     * chat is then filed without a department.
     */
    static Long activeDepartment(Object value, DepartmentService departments) {
        Long id = integerLong(value);
        if (id == null) {
            return null;
        }
        try {
            Department department = departments.findById(id);
            return department != null && department.isActive() ? department.getId() : null;
        } catch (EntityNotFoundException ex) {
            return null;
        }
    }

    /** A whole number sent as a JSON number or numeric text; anything else is {@code null}. */
    static Integer integer(Object value) {
        Long number = integerLong(value);
        return number == null || number > Integer.MAX_VALUE || number < Integer.MIN_VALUE
                ? null : number.intValue();
    }

    private static Long integerLong(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            return ((Number) value).longValue();
        }
        if (value instanceof String text && text.trim().matches("-?\\d{1,18}")) {
            return Long.parseLong(text.trim());
        }
        return null;
    }

    /** A 422 in the shape the widget reads: {@code {message, errors: {field: [message]}}}. */
    static ResponseEntity<Map<String, Object>> invalid(String field, String message) {
        return ResponseEntity.status(422).body(Map.of(
                "message", message,
                "errors", Map.of(field, List.of(message))));
    }
}
