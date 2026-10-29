package dev.escalated.plugins;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The SDK's HTTP contract version 1 on this host, as escalated-laravel's PluginHttp: what a handler
 * receives, and how its answer becomes a response.
 */
final class PluginHttp {

    /** Caps a body, so the bytes, their base64 and the parsed body fit one 10 MB runtime message. */
    static final int MAX_BODY_BYTES = 3 * 1024 * 1024;

    /** Never reach plugin code: the caller's browser and proxy credentials. */
    private static final Set<String> WITHHELD = Set.of("cookie", "authorization", "x-xsrf-token", "x-csrf-token");

    private static final Set<String> BLOCKED_RESPONSE_HEADERS = Set.of("connection", "content-length",
            "transfer-encoding", "keep-alive", "upgrade", "proxy-authenticate", "proxy-authorization", "te", "trailer",
            "set-cookie");
    private static final Pattern HEADER_TOKEN = Pattern.compile("^[!#$%&'*+.^_`|~0-9a-z-]+$");

    private PluginHttp() {
    }

    /** A body over {@link #MAX_BODY_BYTES}. */
    static final class TooLargeException extends IOException {
        TooLargeException() {
            super("Request Entity Too Large");
        }
    }

    /** A plugin answer this host will not send. */
    static final class InvalidResponseException extends Exception {
        InvalidResponseException(String message) {
            super(message);
        }
    }

    /** An endpoint's HTTP answer. */
    record Reply(int status, Map<String, String> headers, byte[] body) {
    }

    static PluginRequest request(HttpServletRequest http, String declaredPath, Map<String, String> params)
            throws IOException {
        byte[] raw = readBody(http);
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : Collections.list(http.getHeaderNames())) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (WITHHELD.contains(lower) || lower.startsWith("proxy-")) {
                continue;
            }
            headers.put(lower, String.join(", ", Collections.list(http.getHeaders(name))));
        }
        return new PluginRequest(http.getMethod(), declaredPath, parseBody(http.getContentType(), raw), raw, params,
                query(http.getQueryString()), headers, http.getRemoteAddr());
    }

    private static byte[] readBody(HttpServletRequest http) throws IOException {
        if (http.getContentLengthLong() > MAX_BODY_BYTES) {
            throw new TooLargeException();
        }
        try (InputStream in = http.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new TooLargeException();
            }
            return bytes;
        }
    }

    /** The query string, a repeated key joined with commas. */
    static Map<String, String> query(String queryString) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (queryString != null && !queryString.isEmpty()) {
            for (String pair : queryString.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String key = decode(eq < 0 ? pair : pair.substring(0, eq));
                String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
                if (key != null && value != null) {
                    values.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                }
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        values.forEach((key, list) -> out.put(key, String.join(",", list)));
        return out;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static Object parseBody(String contentType, byte[] raw) {
        if (raw.length == 0 || contentType == null) {
            return null;
        }
        String type = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("application/json".equals(type) || type.endsWith("+json")) {
            return PluginJson.read(raw);
        }
        if ("application/x-www-form-urlencoded".equals(type)) {
            Map<String, Object> out = new LinkedHashMap<>();
            Map<String, List<String>> values = new LinkedHashMap<>();
            for (String pair : new String(raw, StandardCharsets.UTF_8).split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String key = decode(eq < 0 ? pair : pair.substring(0, eq));
                String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
                if (key == null || value == null) {
                    return null;
                }
                values.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
            values.forEach((key, list) -> out.put(key, list.size() == 1 ? list.get(0) : new ArrayList<Object>(list)));
            return out;
        }
        return null;
    }

    /**
     * A handler's result as a response: an {@code httpResponse()} envelope (or a {@link PluginResponse})
     * chooses the status, format and headers, validated independently of the SDK; anything else is
     * JSON with 200.
     */
    static Reply reply(Object result) throws InvalidResponseException {
        if (result instanceof PluginResponse response) {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("__escalated_http", 1);
            envelope.put("status", response.status() == 0 ? 200 : response.status());
            envelope.put("format", response.text() ? "text" : "json");
            envelope.put("headers", response.headers() == null ? Map.of() : new LinkedHashMap<>(response.headers()));
            envelope.put("body", response.body());
            return reply(envelope);
        }
        Map<String, String> jsonType = new LinkedHashMap<>();
        jsonType.put("content-type", "application/json;charset=UTF-8");
        Map<String, Object> envelope = PluginJson.record(result);
        if (envelope == null || envelope.get("__escalated_http") == null) {
            return new Reply(200, jsonType, json(result));
        }
        Object status = envelope.get("status");
        Object format = envelope.get("format");
        Map<String, Object> hdrs = PluginJson.record(envelope.get("headers"));
        if (!PluginJson.same(envelope.get("__escalated_http"), 1) || !(status instanceof Number number)
                || PluginJson.id(number) == null || PluginJson.id(number) < 200 || PluginJson.id(number) > 599
                || !("json".equals(format) || "text".equals(format)) || hdrs == null || !envelope.containsKey("body")) {
            throw new InvalidResponseException("invalid plugin HTTP response");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, Object> header : hdrs.entrySet()) {
            String lower = header.getKey().toLowerCase(Locale.ROOT);
            if (!HEADER_TOKEN.matcher(lower).matches() || BLOCKED_RESPONSE_HEADERS.contains(lower)
                    || !(header.getValue() instanceof String value) || value.chars().anyMatch(c -> c < 0x20 || c == 0x7f)
                    || headers.containsKey(lower)) {
                throw new InvalidResponseException("invalid plugin HTTP response: header \"" + header.getKey() + "\"");
            }
            headers.put(lower, value);
        }
        int code = PluginJson.id(number).intValue();
        if ("text".equals(format)) {
            if (!(envelope.get("body") instanceof String text)) {
                throw new InvalidResponseException("invalid plugin HTTP response: a text response needs a string body");
            }
            headers.putIfAbsent("content-type", "text/plain;charset=UTF-8");
            return new Reply(code, headers, text.getBytes(StandardCharsets.UTF_8));
        }
        headers.putIfAbsent("content-type", jsonType.get("content-type"));
        return new Reply(code, headers, json(envelope.get("body")));
    }

    private static byte[] json(Object value) throws InvalidResponseException {
        try {
            return PluginJson.write(value).getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new InvalidResponseException("invalid plugin HTTP response: " + ex.getMessage());
        }
    }
}
