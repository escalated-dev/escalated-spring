package dev.escalated.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.escalated.config.EscalatedProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.ModelAndView;

/**
 * The Inertia protocol (https://inertiajs.com/the-protocol) for Escalated's pages.
 *
 * <ul>
 *   <li>A first visit gets an HTML document with the page object in {@code data-page}
 *       ({@link InertiaRootTemplate}, or the Thymeleaf view {@code escalated.ui.root-view}).
 *   <li>A visit carrying {@code X-Inertia} gets the page object as JSON, with {@code X-Inertia:
 *       true} and {@code Vary: X-Inertia}.
 *   <li>A partial reload ({@code X-Inertia-Partial-Component} naming this page) gets only the
 *       props in {@code X-Inertia-Partial-Data}, less those in {@code X-Inertia-Partial-Except}.
 *       {@code errors} always comes back. A prop given as a {@link Supplier} is evaluated only
 *       when it is sent.
 *   <li>Writes answer with {@link #redirect} or {@link #back}: 303 after anything but a GET.
 *   <li>The version check and its 409 live in {@link InertiaFilter}.
 * </ul>
 */
public class Inertia {

    /** Session key of the flash messages the next page shows once. */
    public static final String FLASH_SESSION_KEY = "escalated.inertia.flash";

    /** Session key of the validation errors the next page shows once. */
    public static final String ERRORS_SESSION_KEY = "escalated.inertia.errors";

    public static final String HEADER = "X-Inertia";
    public static final String VERSION_HEADER = "X-Inertia-Version";
    public static final String LOCATION_HEADER = "X-Inertia-Location";
    public static final String PARTIAL_COMPONENT_HEADER = "X-Inertia-Partial-Component";
    public static final String PARTIAL_DATA_HEADER = "X-Inertia-Partial-Data";
    public static final String PARTIAL_EXCEPT_HEADER = "X-Inertia-Partial-Except";

    private static final ObjectMapper JSON = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final EscalatedProperties properties;
    private final List<InertiaSharedProps> shared;
    private final InertiaRootTemplate template;
    private final UiRoutes routes;

    public Inertia(
            EscalatedProperties properties,
            List<InertiaSharedProps> shared,
            InertiaRootTemplate template,
            UiRoutes routes) {
        this.properties = properties;
        this.shared = shared;
        this.template = template;
        this.routes = routes;
    }

    /** Renders {@code component} with {@code props} for the current request. */
    public Object render(String component, Map<String, Object> props) {
        HttpServletRequest request = currentRequest();
        Map<String, Object> page = page(request, component, props);
        String pageJson = toJson(page);

        if (isInertia(request)) {
            return ResponseEntity.ok()
                    .header(HEADER, "true")
                    .header(HttpHeaders.VARY, HEADER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(pageJson);
        }

        String rootView = properties.getUi().getRootView();
        if (rootView != null && !rootView.isBlank()) {
            ModelAndView view = new ModelAndView(rootView);
            view.addObject("page", page);
            view.addObject("pageJson", pageJson);
            view.addObject("routesScript", routes.script());
            view.addObject("csrfToken", DefaultInertiaRootTemplate.csrfToken(request));
            return view;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.VARY, HEADER)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(template.render(page, pageJson, request));
    }

    /** The page object for {@code component}: shared props first, the page's own over them. */
    public Map<String, Object> page(HttpServletRequest request, String component, Map<String, Object> props) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (InertiaSharedProps contributor : shared) {
            merged.putAll(contributor.share(request));
        }
        merged.putAll(props);

        boolean partial = isInertia(request) && component.equals(request.getHeader(PARTIAL_COMPONENT_HEADER));
        Set<String> only = partial ? headerList(request, PARTIAL_DATA_HEADER) : Set.of();
        Set<String> except = partial ? headerList(request, PARTIAL_EXCEPT_HEADER) : Set.of();

        Map<String, Object> resolved = new LinkedHashMap<>();
        merged.forEach((key, value) -> {
            boolean always = "errors".equals(key);
            if (!always && !only.isEmpty() && !only.contains(key)) {
                return;
            }
            if (!always && except.contains(key)) {
                return;
            }
            resolved.put(key, value instanceof Supplier<?> supplier ? supplier.get() : value);
        });

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("component", component);
        page.put("props", resolved);
        page.put("url", url(request));
        page.put("version", properties.getUi().getVersion());
        page.put("clearHistory", false);
        page.put("encryptHistory", false);
        return page;
    }

    /** Redirects: 302 for a GET, 303 for anything else, so the browser follows with a GET. */
    public ResponseEntity<Void> redirect(String location) {
        HttpServletRequest request = currentRequest();
        int status = "GET".equalsIgnoreCase(request.getMethod()) ? 302 : 303;
        return ResponseEntity.status(status).location(URI.create(location)).build();
    }

    /**
     * Redirects to the page the write came from (its {@code Referer}, when that is this host), or
     * to {@code fallback}.
     */
    public ResponseEntity<Void> back(String fallback) {
        HttpServletRequest request = currentRequest();
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                if (uri.getHost() == null || uri.getHost().equalsIgnoreCase(request.getServerName())) {
                    String path = uri.getRawPath();
                    if (path != null && path.startsWith("/") && !path.startsWith("//")) {
                        return redirect(path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // A malformed Referer falls back.
            }
        }
        return redirect(fallback);
    }

    /** Shows {@code message} once on the next page, as {@code flash.<key>} ({@code success}, {@code error}). */
    public void flash(String key, String message) {
        HttpSession session = currentRequest().getSession(true);
        Map<String, Object> flash = sessionMap(session, FLASH_SESSION_KEY);
        flash.put(key, message);
        session.setAttribute(FLASH_SESSION_KEY, flash);
    }

    /**
     * Shows structured data once on the next page, as {@code flash.<key>} (for example
     * {@code two_factor_setup}). Kept in the server-side session, never in a cookie.
     */
    public void flash(String key, Object value) {
        HttpSession session = currentRequest().getSession(true);
        Map<String, Object> flash = sessionMap(session, FLASH_SESSION_KEY);
        flash.put(key, value);
        session.setAttribute(FLASH_SESSION_KEY, flash);
    }

    /** Hands validation errors (field to messages) to the next page, as {@code errors}: the first message each. */
    public void errors(Map<String, List<String>> errors) {
        HttpSession session = currentRequest().getSession(true);
        Map<String, Object> first = new LinkedHashMap<>();
        errors.forEach((field, messages) -> first.put(field, messages.isEmpty() ? "" : messages.get(0)));
        session.setAttribute(ERRORS_SESSION_KEY, first);
    }

    public static boolean isInertia(HttpServletRequest request) {
        return request.getHeader(HEADER) != null;
    }

    public static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Inertia props must serialise to JSON", exception);
        }
    }

    static String url(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null || query.isEmpty() ? "" : "?" + query);
    }

    /** Reads and removes a one-shot session map (flash, errors). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> pull(HttpServletRequest request, String key) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> value = sessionMap(session, key);
        session.removeAttribute(key);
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> sessionMap(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    private static Set<String> headerList(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new IllegalStateException("Inertia responses need a current HTTP request");
    }
}
