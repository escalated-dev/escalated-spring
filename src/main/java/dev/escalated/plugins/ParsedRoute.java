package dev.escalated.plugins;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One endpoint or webhook, validated as escalated-laravel's RouteRegistrar does. {@code path} is the
 * declared path, which is what a plugin is dispatched on.
 */
record ParsedRoute(String method, String path, String capability, PluginRoute.Handler handler, String[] segments) {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");
    private static final Pattern PARAM = Pattern.compile("^:([A-Za-z_][A-Za-z0-9_]*)$");

    /** Refuses unknown methods and paths that are not absolute or hold "..", control characters, "?", "#" or "\". */
    static ParsedRoute parse(String plugin, String method, String path, String capability, PluginRoute.Handler handler) {
        String verb = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        String bad = "plugin " + plugin + ": route \"" + method + " " + path + "\" is not METHOD /path";
        if (!METHODS.contains(verb) || path == null || !path.startsWith("/") || path.matches(".*[?#\\\\ ].*")) {
            throw new IllegalArgumentException(bad);
        }
        for (char c : path.toCharArray()) {
            if (c < 0x20 || c == 0x7f) {
                throw new IllegalArgumentException(bad);
            }
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException(bad);
            }
        }
        return new ParsedRoute(verb, path, capability == null || capability.isBlank() ? null : capability, handler,
                segments);
    }

    /** The route params when {@code rawPath} (still percent-encoded) matches, else null. */
    Map<String, String> match(String requestMethod, String rawPath) {
        if (!method.equals(requestMethod)) {
            return null;
        }
        String[] parts = rawPath.split("/", -1);
        if (parts.length != segments.length) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < segments.length; i++) {
            var matcher = PARAM.matcher(segments[i]);
            if (matcher.matches()) {
                if (parts[i].isEmpty()) {
                    return null;
                }
                try {
                    params.put(matcher.group(1), URLDecoder.decode(parts[i].replace("+", "%2B"), StandardCharsets.UTF_8));
                } catch (IllegalArgumentException ex) {
                    return null;
                }
                continue;
            }
            if (!parts[i].equals(segments[i])) {
                return null;
            }
        }
        return params;
    }

    record Match(ParsedRoute route, Map<String, String> params) {
    }

    static Match find(List<ParsedRoute> routes, String method, String rawPath) {
        for (ParsedRoute route : routes) {
            Map<String, String> params = route.match(method, rawPath);
            if (params != null) {
                return new Match(route, params);
            }
        }
        return null;
    }
}
