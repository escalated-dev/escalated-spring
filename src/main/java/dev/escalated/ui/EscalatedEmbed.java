package dev.escalated.ui;

import dev.escalated.config.EscalatedProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The admin embedded in the host's own admin panel (ADR 2026-11-05-embeddable-admin): the
 * effective {@code escalated.ui.embed} settings and the path arithmetic every embed piece shares.
 *
 * <p>Optional and off by default. Whether it is on, and the prefix, are fixed when the context
 * starts (the filters are registered and the {@code route()} names moved then); the mode, the gate
 * switch, the layout, the root view and the frame ancestors are read on every request.
 *
 * <p>While it is on, every admin page under {@code /escalated/admin} answers under
 * {@code /{prefix}} instead, with the same handlers and route names, and the old URLs answer 404.
 * The two-factor enrolment pages ({@code /escalated/admin/settings/two-factor}) are every staff
 * user's own and stay where they are.
 */
public class EscalatedEmbed {

    /** Marks a request served by the embedded admin. */
    public static final String REQUEST_ATTRIBUTE = "escalated.embedded";

    /** The embedded request's own URI (context path included), before it was handed to the admin. */
    public static final String ORIGINAL_URI_ATTRIBUTE = "escalated.embedded.uri";

    public static final String LAYOUT = "layout";
    public static final String IFRAME = "iframe";

    /** Where the standalone admin lives. */
    public static final String ADMIN = UiRoutes.PREFIX + "/admin";

    /** Staff-wide pages below the admin that never move. */
    private static final String TWO_FACTOR = ADMIN + "/settings/two-factor";

    private final EscalatedProperties.EmbedProperties config;
    private final boolean enabled;
    private final String prefix;

    public EscalatedEmbed(EscalatedProperties properties) {
        this(properties, List.of());
    }

    /** Applies each configurer to {@code escalated.ui.embed} first, in order. */
    public EscalatedEmbed(EscalatedProperties properties, List<EscalatedEmbedConfigurer> configurers) {
        this.config = properties.getUi().getEmbed();
        for (EscalatedEmbedConfigurer configurer : configurers) {
            configurer.configure(config);
        }
        this.enabled = config.isEnabled();
        String trimmed = config.getPrefix() == null ? "" : config.getPrefix().trim().replaceAll("^/+|/+$", "");
        this.prefix = trimmed.isEmpty() ? "admin/support" : trimmed;
    }

    public boolean enabled() {
        return enabled;
    }

    /** {@code layout} or {@code iframe}; anything else reads as {@code layout}. */
    public String mode() {
        return IFRAME.equals(config.getMode()) ? IFRAME : LAYOUT;
    }

    public boolean iframe() {
        return enabled && IFRAME.equals(mode());
    }

    /** The prefix without slashes, e.g. {@code admin/support}. */
    public String prefix() {
        return prefix;
    }

    /** Where the embedded admin lives: {@code /admin/support}. */
    public String basePath() {
        return "/" + prefix;
    }

    public boolean replacesAdminGate() {
        return enabled && config.isReplaceAdminGate();
    }

    public String layout() {
        return blankToNull(config.getLayout());
    }

    public String rootView() {
        return blankToNull(config.getRootView());
    }

    /** The host's filter bean names for embedded requests; empty for none. */
    public List<String> middleware() {
        List<String> names = config.getMiddleware();
        if (names == null) {
            return List.of();
        }
        return names.stream().filter(name -> name != null && !name.isBlank()).map(String::trim).toList();
    }

    /**
     * Origins allowed to frame the admin: exact {@code scheme://host[:port]} http(s) origins.
     * {@code *}, wildcards, paths, credentials and anything else are dropped.
     */
    public List<String> frameAncestors() {
        List<String> origins = new ArrayList<>();
        for (String entry : config.getFrameAncestors()) {
            String origin = origin(entry);
            if (origin != null && !origins.contains(origin)) {
                origins.add(origin);
            }
        }
        return origins;
    }

    /** {@code frame-ancestors 'self' <origins>}; {@code 'self'} alone when no origin is allowed. */
    public String frameAncestorsPolicy() {
        StringBuilder policy = new StringBuilder("frame-ancestors 'self'");
        for (String origin : frameAncestors()) {
            policy.append(' ').append(origin);
        }
        return policy.toString();
    }

    /** Whether {@code request} is served by the embedded admin. */
    public static boolean isEmbedded(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(REQUEST_ATTRIBUTE));
    }

    /** Whether a path (no context path) is an admin page that moves while embedding is on. */
    public static boolean moves(String path) {
        if (path == null || !(path.equals(ADMIN) || path.startsWith(ADMIN + "/"))) {
            return false;
        }
        return !(path.equals(TWO_FACTOR) || path.startsWith(TWO_FACTOR + "/"));
    }

    /** Whether a path (no context path) is under the embed prefix. */
    public boolean underPrefix(String path) {
        String base = basePath();
        return path != null && (path.equals(base) || path.startsWith(base + "/"));
    }

    /** The embedded path of a standalone admin path ({@code /escalated/admin/x} to {@code /admin/support/x}). */
    public String toEmbedded(String path) {
        if (!enabled || !moves(path)) {
            return path;
        }
        return basePath() + path.substring(ADMIN.length());
    }

    /** The standalone admin path an embedded path is served by ({@code /admin/support/x} to {@code /escalated/admin/x}). */
    public String toStandalone(String path) {
        if (!enabled || !underPrefix(path)) {
            return path;
        }
        return ADMIN + path.substring(basePath().length());
    }

    /**
     * A redirect target with its standalone admin path moved under the prefix: a path or an
     * absolute URL, with or without the context path. Anything else is returned as it is.
     */
    public String rewriteLocation(String location, String contextPath) {
        if (!enabled || location == null || location.isEmpty()) {
            return location;
        }
        String context = contextPath == null ? "" : contextPath;
        int start = 0;
        if (!location.startsWith("/") || location.startsWith("//")) {
            int scheme = location.indexOf("://");
            if (scheme < 0) {
                return location;
            }
            start = location.indexOf('/', scheme + 3);
            if (start < 0) {
                return location;
            }
        }
        int end = location.length();
        for (char stop : new char[] {'?', '#'}) {
            int at = location.indexOf(stop, start);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        String path = location.substring(start, end);
        if (!path.startsWith(context)) {
            return location;
        }
        String inner = path.substring(context.length());
        if (!moves(inner)) {
            return location;
        }
        return location.substring(0, start) + context + toEmbedded(inner) + location.substring(end);
    }

    /**
     * {@code escalated.embed} for a page: {@code enabled} only on embedded responses, the route
     * name of the embedded page, and the origins the iframe bridge may talk to.
     */
    public Map<String, Object> prop(HttpServletRequest request, UiRoutes routes) {
        boolean embedded = isEmbedded(request);
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("enabled", embedded);
        embed.put("mode", mode());
        embed.put("layout", layout());
        embed.put("route", embedded ? routes.nameOf(currentPath(request)) : null);
        embed.put("base_url", basePath());
        embed.put("allowed_origins", IFRAME.equals(mode()) ? frameAncestors() : List.of());
        return embed;
    }

    /** The request's path as the browser sent it, without the context path. */
    public static String currentPath(HttpServletRequest request) {
        Object original = request.getAttribute(ORIGINAL_URI_ATTRIBUTE);
        String uri = original instanceof String value ? value : request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        return uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    private static String origin(String entry) {
        if (entry == null) {
            return null;
        }
        String value = entry.trim();
        if (value.isEmpty() || value.contains("*")) {
            return null;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                return null;
            }
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                return null;
            }
            String path = uri.getRawPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) {
                return null;
            }
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (java.net.URISyntaxException malformed) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
