package dev.escalated.controllers.widget;

import java.util.Map;
import org.springframework.http.HttpStatus;
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
}
