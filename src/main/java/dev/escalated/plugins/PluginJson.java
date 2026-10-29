package dev.escalated.plugins;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Values as plugins see them: what JSON can carry (maps, lists, numbers, strings, booleans and
 * null), the same whether the plugin runs in this process or in the runtime.
 */
final class PluginJson {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private PluginJson() {
    }

    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("The value cannot be written as JSON: " + ex.getOriginalMessage(), ex);
        }
    }

    static Object read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    static Object read(byte[] json) {
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    static Object fromNode(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : MAPPER.convertValue(node, Object.class);
    }

    /** {@code value} with the shape JSON gives it: a plain copy, so a plugin cannot change the caller's. */
    static Object plain(Object value) {
        if (value == null) {
            return null;
        }
        return read(write(value));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> record(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /** A plain copy of an object, or an empty one. */
    static Map<String, Object> plainRecord(Object value) {
        Map<String, Object> copy = record(plain(value));
        return copy == null ? new LinkedHashMap<>() : copy;
    }

    /** {@code value} with every number as a stripped BigDecimal, so that 1 and 1.0 compare equal. */
    static Object canonical(Object value) {
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros();
            } catch (NumberFormatException ex) {
                return number.toString();
            }
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), canonical(v)));
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            list.forEach(item -> out.add(canonical(item)));
            return out;
        }
        return value;
    }

    static boolean same(Object a, Object b) {
        return java.util.Objects.equals(canonical(a), canonical(b));
    }

    /** A string parameter; a whole number is written without a decimal point. */
    static String string(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return ((BigDecimal) canonical(number)).toPlainString();
        }
        return String.valueOf(value);
    }

    /** An id: a whole number, given as a number or a numeric string; null otherwise. */
    static Long id(Object value) {
        if (value instanceof Number number) {
            BigDecimal decimal = (BigDecimal) canonical(number);
            try {
                return decimal.longValueExact();
            } catch (ArithmeticException ex) {
                return null;
            }
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
