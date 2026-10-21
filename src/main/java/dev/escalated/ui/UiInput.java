package dev.escalated.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.services.ValidationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a page submitted. Inertia sends JSON, or multipart form data once a file is attached, in
 * which arrays arrive as {@code key[0]}, {@code key[1]}. Both read the same here.
 */
public final class UiInput {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern INDEXED = Pattern.compile("^([^\\[]+)\\[(\\d*)]$");

    private final Map<String, Object> values;

    private UiInput(Map<String, Object> values) {
        this.values = values;
    }

    public static UiInput of(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {
            try {
                Object parsed = JSON.readValue(request.getInputStream(), Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    Map<String, Object> values = new LinkedHashMap<>();
                    map.forEach((key, value) -> values.put(String.valueOf(key), value));
                    return new UiInput(values);
                }
                return new UiInput(new LinkedHashMap<>());
            } catch (IOException malformed) {
                throw invalid("body", "The request body is not valid JSON.");
            }
        }

        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, TreeMap<Integer, Object>> arrays = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, given) -> {
            Matcher indexed = INDEXED.matcher(key);
            if (indexed.matches()) {
                TreeMap<Integer, Object> items = arrays.computeIfAbsent(indexed.group(1), k -> new TreeMap<>());
                if (indexed.group(2).isEmpty()) {
                    for (String value : given) {
                        items.put(items.size(), value);
                    }
                } else {
                    items.put(Integer.parseInt(indexed.group(2)), given.length == 0 ? null : given[0]);
                }
            } else {
                values.put(key, given.length == 1 ? given[0] : List.of(given));
            }
        });
        arrays.forEach((key, items) -> values.put(key, new ArrayList<>(items.values())));
        return new UiInput(values);
    }

    public static UiInput of(Map<String, Object> values) {
        return new UiInput(new LinkedHashMap<>(values));
    }

    public Map<String, Object> all() {
        return values;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Object raw(String key) {
        return values.get(key);
    }

    /** A trimmed string, or null when absent or blank. */
    public String string(String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public String requiredString(String key, int max) {
        String value = string(key);
        if (value == null) {
            throw invalid(key, "The " + label(key) + " field is required.");
        }
        if (value.length() > max) {
            throw invalid(key, "The " + label(key) + " field must not be greater than " + max + " characters.");
        }
        return value;
    }

    /** A whole number, null when absent or blank; anything else is a validation error. */
    public Long longValue(String key) {
        Object value = values.get(key);
        if (value == null || (value instanceof String text && text.isBlank())) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException notANumber) {
            throw invalid(key, "The " + label(key) + " field must be an integer.");
        }
    }

    public long requiredLong(String key) {
        Long value = longValue(key);
        if (value == null) {
            throw invalid(key, "The " + label(key) + " field is required.");
        }
        return value;
    }

    public boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text);
    }

    /** A list: a JSON array, indexed form fields, or one value. Null when absent. */
    public List<Object> list(String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        List<Object> one = new ArrayList<>();
        if (!(value instanceof String text && text.isEmpty())) {
            one.add(value);
        }
        return one;
    }

    /** A list of whole numbers. */
    public List<Long> longs(String key) {
        List<Object> items = list(key);
        List<Long> numbers = new ArrayList<>();
        if (items == null) {
            return numbers;
        }
        for (Object item : items) {
            if (item instanceof Number number) {
                numbers.add(number.longValue());
            } else if (item != null && !String.valueOf(item).isBlank()) {
                try {
                    numbers.add(Long.parseLong(String.valueOf(item).trim()));
                } catch (NumberFormatException notANumber) {
                    throw invalid(key, "The " + label(key) + " field must contain integers.");
                }
            }
        }
        return numbers;
    }

    public static ValidationFailedException invalid(String key, String message) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        errors.put(key, List.of(message));
        return new ValidationFailedException(errors);
    }

    private static String label(String key) {
        return key.replace('_', ' ');
    }
}
