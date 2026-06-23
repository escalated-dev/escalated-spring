package dev.escalated.controllers.newsletter;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class NewsletterHttpSupport {

    private NewsletterHttpSupport() {}

    public static Map<String, Object> inertia(String component, Map<String, Object> props) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("component", component);
        body.put("props", props);
        return body;
    }

    public static Map<String, Object> redirect(String url) {
        return Map.of("redirect", url);
    }

    public static String requiredString(Map<String, Object> body, String key, Integer max) {
        Object value = body.get(key);
        if (!(value instanceof String str) || str.isEmpty()) {
            throw badRequest(key + " is required");
        }
        if (max != null && str.length() > max) {
            throw badRequest(key + " may not be greater than " + max + " characters");
        }
        return str;
    }

    public static String optionalString(Map<String, Object> body, String key, Integer max) {
        Object value = body.get(key);
        if (value == null || "".equals(value)) {
            return null;
        }
        if (!(value instanceof String str)) {
            throw badRequest(key + " must be a string");
        }
        if (max != null && str.length() > max) {
            throw badRequest(key + " may not be greater than " + max + " characters");
        }
        return str;
    }

    public static long requiredInteger(Map<String, Object> body, String key, Integer min, Integer max) {
        Object value = body.get(key);
        Long parsed = parseLong(value);
        if (parsed == null) {
            throw badRequest(key + " must be an integer");
        }
        if (min != null && parsed < min) {
            throw badRequest(key + " must be at least " + min);
        }
        if (max != null && parsed > max) {
            throw badRequest(key + " must be at most " + max);
        }
        return parsed;
    }

    public static Long optionalInteger(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || "".equals(value)) {
            return null;
        }
        Long parsed = parseLong(value);
        if (parsed == null) {
            throw badRequest(key + " must be an integer");
        }
        return parsed;
    }

    public static boolean requiredBoolean(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String str) {
            if ("true".equalsIgnoreCase(str) || "1".equals(str)) {
                return true;
            }
            if ("false".equalsIgnoreCase(str) || "0".equals(str)) {
                return false;
            }
        }
        throw badRequest(key + " must be a boolean");
    }

    public static String assertEmail(String value, String key, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw badRequest(key + " must be a valid email");
            }
            return null;
        }
        if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || value.length() > 320) {
            throw badRequest(key + " must be a valid email");
        }
        return value;
    }

    public static String assertOneOf(Object value, String key, Set<String> allowed) {
        if (value == null || !allowed.contains(String.valueOf(value))) {
            throw badRequest(key + " must be one of " + String.join(", ", allowed));
        }
        return String.valueOf(value);
    }

    public static Instant optionalDateAfterNow(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || "".equals(value)) {
            return null;
        }
        Instant instant = parseInstant(value);
        if (instant == null || !instant.isAfter(Instant.now())) {
            throw badRequest(key + " must be a future date");
        }
        return instant;
    }

    public static String filterJsonFromBody(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || "".equals(value)) {
            return null;
        }
        if (value instanceof String str) {
            return str;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
            } catch (Exception ex) {
                throw badRequest(key + " must be an array");
            }
        }
        throw badRequest(key + " must be an array");
    }

    public static void abort422(String message) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public static String decodeTrackedUrl(String encoded) {
        String normalized = encoded.replace('-', '+').replace('_', '/');
        int pad = (4 - (normalized.length() % 4)) % 4;
        String padded = normalized + "=".repeat(pad);
        String decoded = new String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8);
        if (decoded.isBlank()) {
            throw badRequest("Bad request");
        }
        try {
            URI uri = URI.create(decoded);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw badRequest("Bad request");
            }
        } catch (Exception ex) {
            throw badRequest("Bad request");
        }
        return decoded;
    }

    public static List<String> discoverThemes(java.nio.file.Path themesDir) {
        java.util.Set<String> themes = new java.util.LinkedHashSet<>();
        if (java.nio.file.Files.isDirectory(themesDir)) {
            try (var stream = java.nio.file.Files.list(themesDir)) {
                stream.filter(p -> p.toString().endsWith(".html"))
                        .forEach(p -> themes.add(p.getFileName().toString().replace(".html", "")));
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (themes.isEmpty()) {
            return List.of("default", "branded");
        }
        return List.copyOf(themes);
    }

    private static Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && str.matches("-?\\d+")) {
            return Long.parseLong(str);
        }
        return null;
    }

    private static Instant parseInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String str) {
            try {
                return Instant.parse(str);
            } catch (Exception ignored) {
                // try ISO local date-time without zone
            }
        }
        return null;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
