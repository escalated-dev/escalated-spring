package dev.escalated.widget;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.services.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Decides which other sites may embed the widget. The list is
 * {@code escalated.widget.allowed-origins} (env
 * {@code ESCALATED_WIDGET_ALLOWED_ORIGINS}, comma separated) plus the admin
 * setting {@code widget_allowed_origins} (one origin per line or comma
 * separated).
 *
 * <p>An entry is a bare origin, {@code scheme://host[:port]} over http or
 * https, or {@code https://*.example.com}, which matches every subdomain of
 * example.com at any depth but not example.com itself.
 */
@Component
public class WidgetOrigins {

    /** The admin setting holding extra origins. */
    public static final String SETTING = "widget_allowed_origins";

    /** Request attribute set to the Origin of an allowed request from another site. */
    public static final String ATTRIBUTE = "escalated.widget.cross_origin";

    private static final Pattern ENTRY = Pattern.compile("^(https?)://(\\*\\.)?([^/?#*]+?)/?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORIGIN = Pattern.compile(
            "^(https?)://([^\\s/?#@:\\[\\]]+|\\[[0-9a-f:.]+\\])(?::(\\d{1,5}))?/?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATORS = Pattern.compile("[\\s,]+");

    private final EscalatedProperties properties;
    private final SettingsService settings;

    public WidgetOrigins(EscalatedProperties properties, SettingsService settings) {
        this.properties = properties;
        this.settings = settings;
    }

    /** Whether the configured list or the admin setting allows the origin. */
    public boolean allows(String origin) {
        String normalized = normalize(origin);
        if (normalized == null) {
            return false;
        }
        for (String pattern : configured()) {
            if (matches(pattern, normalized)) {
                return true;
            }
        }
        return false;
    }

    /** The normalised patterns from config and the admin setting, invalid entries left out. */
    public List<String> configured() {
        List<String> entries = new ArrayList<>(properties.getWidget().getAllowedOrigins());
        entries.addAll(split(settings.getOrDefault(SETTING, "")));
        return patterns(entries);
    }

    /**
     * The request's Origin when it names another site, or null for
     * same-origin requests and requests without an Origin header.
     */
    public static String crossOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isEmpty() || "null".equals(origin)) {
            return null;
        }
        origin = normalize(origin);
        if (origin == null) {
            return null;
        }
        String own = normalize(request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort());
        return origin.equals(own) ? null : origin;
    }

    /** The entries of a setting value that are not origins, in the order given. */
    public static List<String> invalidEntries(String value) {
        List<String> invalid = new ArrayList<>();
        for (String entry : split(value)) {
            if (normalizePattern(entry) == null) {
                invalid.add(entry);
            }
        }
        return invalid;
    }

    /**
     * Normalise an admin-entered origin or {@code *.} pattern, or null when it
     * is not a bare http(s) origin.
     */
    public static String normalizePattern(String entry) {
        if (entry == null) {
            return null;
        }
        Matcher m = ENTRY.matcher(entry.trim());
        if (!m.matches()) {
            return null;
        }
        String host = normalize(m.group(1).toLowerCase(Locale.ROOT) + "://" + m.group(3));
        if (host == null) {
            return null;
        }
        return m.group(2) != null ? host.replace("://", "://*.") : host;
    }

    /** {@code scheme://host[:port]}, lowercased, default ports dropped; null when not a bare origin. */
    static String normalize(String origin) {
        if (origin == null) {
            return null;
        }
        Matcher m = ORIGIN.matcher(origin.trim());
        if (!m.matches()) {
            return null;
        }
        String scheme = m.group(1).toLowerCase(Locale.ROOT);
        String host = m.group(2).toLowerCase(Locale.ROOT);
        if (m.group(3) == null) {
            return scheme + "://" + host;
        }
        int port = Integer.parseInt(m.group(3));
        if (port < 1 || port > 65535) {
            return null;
        }
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    static boolean matches(String pattern, String origin) {
        int wildcard = pattern.indexOf("://*.");
        if (wildcard < 0) {
            return pattern.equals(origin);
        }
        String scheme = pattern.substring(0, wildcard);
        String suffix = pattern.substring(wildcard + 5);
        if (!origin.startsWith(scheme + "://")) {
            return false;
        }
        String host = origin.substring(scheme.length() + 3);
        return host.endsWith("." + suffix) && host.length() > suffix.length() + 1;
    }

    private static List<String> patterns(Collection<String> entries) {
        Set<String> patterns = new LinkedHashSet<>();
        for (String entry : entries) {
            String pattern = normalizePattern(entry);
            if (pattern != null) {
                patterns.add(pattern);
            }
        }
        return new ArrayList<>(patterns);
    }

    private static List<String> split(String value) {
        List<String> entries = new ArrayList<>();
        if (value == null) {
            return entries;
        }
        for (String entry : SEPARATORS.split(value)) {
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries;
    }
}
