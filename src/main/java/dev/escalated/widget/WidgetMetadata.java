package dev.escalated.widget;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Host parameters an embedding page sends with a widget ticket or chat
 * (order number, plan, account id, the page URL, ...). They come from the
 * visitor's browser, so only a flat map of short scalar values is kept.
 */
public final class WidgetMetadata {

    public static final int MAX_KEYS = 50;
    public static final int MAX_ENTRIES = 25;
    public static final int MAX_VALUE_LENGTH = 500;
    public static final int MAX_URL_LENGTH = 2048;

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");

    private WidgetMetadata() {
    }

    /**
     * Clean the {@code metadata} a widget request sent. Returns null when
     * nothing is left. Answers 422 only when {@code metadata} is present but
     * not an object, or has more than 50 keys; anything else that does not
     * fit is dropped.
     *
     * <p>{@code embedded_on} is never taken from the page: it is set to the
     * allowed other site the request came from, when there is one.
     */
    public static Map<String, Object> fromRequest(Object raw, HttpServletRequest request) {
        Object origin = request == null ? null : request.getAttribute(WidgetOrigins.ATTRIBUTE);
        return sanitize(raw, origin instanceof String s ? s : null);
    }

    public static Map<String, Object> sanitize(Object raw, String embedOrigin) {
        Map<?, ?> given = Map.of();
        if (raw != null) {
            if (!(raw instanceof Map<?, ?> map)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "metadata must be an object");
            }
            if (map.size() > MAX_KEYS) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "metadata may have at most " + MAX_KEYS + " entries");
            }
            given = map;
        }

        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : given.entrySet()) {
            if (clean.size() >= MAX_ENTRIES) {
                break;
            }
            if (!(entry.getKey() instanceof String key) || !KEY.matcher(key).matches()) {
                continue;
            }
            Object value = scalar(entry.getValue());
            if (value != null) {
                clean.put(key, value);
            }
        }

        if (clean.containsKey("page_url") && !isWebUrl(clean.get("page_url"))) {
            clean.remove("page_url");
        }

        // Recorded by the server from the request's Origin, so a page cannot claim another site.
        clean.remove("embedded_on");
        if (embedOrigin != null) {
            clean.put("embedded_on", embedOrigin);
        }

        return clean.isEmpty() ? null : clean;
    }

    private static Object scalar(Object value) {
        if (value instanceof String text) {
            String trimmed = text.trim();
            // Cut by code point so a surrogate pair is never split.
            if (trimmed.codePointCount(0, trimmed.length()) > MAX_VALUE_LENGTH) {
                trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_VALUE_LENGTH));
            }
            return trimmed;
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return value;
        }
        if (value instanceof Double d) {
            return Double.isFinite(d) ? d : null;
        }
        if (value instanceof Float f) {
            return Float.isFinite(f) ? f.doubleValue() : null;
        }
        if (value instanceof BigInteger || value instanceof BigDecimal) {
            double d = ((Number) value).doubleValue();
            return Double.isFinite(d) ? value : null;
        }
        return null;
    }

    private static boolean isWebUrl(Object value) {
        if (!(value instanceof String url) || url.length() > MAX_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return scheme != null
                    && ("http".equals(scheme.toLowerCase(Locale.ROOT)) || "https".equals(scheme.toLowerCase(Locale.ROOT)))
                    && uri.getHost() != null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }
}
