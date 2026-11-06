package dev.escalated.ui;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UrlPathHelper;

/**
 * Hands an embedded admin request to Escalated's admin (ADR 2026-11-05-embeddable-admin).
 * Registered on the embed prefix only while embedding is on, after Spring Security's filter, so the
 * host's own security chain for the prefix has already run on the request as it was sent.
 *
 * <ol>
 *   <li>The host's {@code escalated.ui.embed.middleware} filters run, in order; any of them may
 *       answer the request itself (a 403, a redirect to the host's sign-in).
 *   <li>The request is then seen at its standalone path ({@code /admin/support/tickets} as
 *       {@code /escalated/admin/tickets}) and passed through Spring Security again, where
 *       Escalated's web chain applies its own guards as on the standalone admin: CSRF, the admin
 *       gate (unless the host replaces it), two-factor and route permissions. Then on to the same
 *       handler. Nothing the admin decides by path changes.
 * </ol>
 */
public class EmbeddedAdminFilter extends GenericFilterBean {

    private final EscalatedEmbed embed;
    private final Supplier<Filter> security;
    private final Function<String, Filter> middleware;

    /**
     * @param security Spring Security's filter chain proxy
     * @param middleware looks up a host filter bean by name
     */
    public EmbeddedAdminFilter(EscalatedEmbed embed, Supplier<Filter> security, Function<String, Filter> middleware) {
        this.embed = embed;
        this.security = security;
        this.middleware = middleware;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest http) || !(response instanceof HttpServletResponse)
                || !EscalatedEmbed.isEmbedded(http)) {
            chain.doFilter(request, response);
            return;
        }
        List<Filter> host = embed.middleware().stream().map(middleware).toList();
        FilterChain admin = (req, res) -> {
            HttpServletRequest current = (HttpServletRequest) req;
            String path = EscalatedEmbed.currentPath(current);
            security.get().doFilter(new StandaloneRequest(current, embed.toStandalone(path)), res, chain);
        };
        new HostChain(host, admin).doFilter(request, response);
    }

    /** The host's filters, then the admin. */
    private static final class HostChain implements FilterChain {

        private final List<Filter> filters;
        private final FilterChain last;
        private int position;

        HostChain(List<Filter> filters, FilterChain last) {
            this.filters = filters;
            this.last = last;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            if (position < filters.size()) {
                filters.get(position++).doFilter(request, response, this);
                return;
            }
            last.doFilter(request, response);
        }
    }

    /**
     * The embedded request at its standalone path. The parsed-path caches of the first pass and the
     * once-per-request marks of the host chain's filters are kept apart, so Escalated's chain and
     * the dispatcher see this request fresh.
     */
    static final class StandaloneRequest extends HttpServletRequestWrapper {

        private static final Set<String> CACHES = Set.of(
                ServletRequestPathUtils.PATH_ATTRIBUTE, UrlPathHelper.PATH_ATTRIBUTE,
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        private final String path;
        private final Map<String, Object> own = new HashMap<>();

        StandaloneRequest(HttpServletRequest request, String path) {
            super(request);
            this.path = path;
        }

        private static boolean isOwn(String name) {
            return CACHES.contains(name) || name.endsWith(".FILTERED");
        }

        @Override
        public String getRequestURI() {
            return getContextPath() + path;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            url.append(getScheme()).append("://").append(getServerName());
            int port = getServerPort();
            if (port > 0 && !("http".equals(getScheme()) && port == 80) && !("https".equals(getScheme()) && port == 443)) {
                url.append(':').append(port);
            }
            return url.append(getRequestURI());
        }

        @Override
        public String getServletPath() {
            return path;
        }

        @Override
        public String getPathInfo() {
            return null;
        }

        @Override
        public Object getAttribute(String name) {
            return isOwn(name) ? own.get(name) : super.getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            Set<String> names = new LinkedHashSet<>();
            for (Enumeration<String> outer = super.getAttributeNames(); outer.hasMoreElements();) {
                String name = outer.nextElement();
                if (!isOwn(name)) {
                    names.add(name);
                }
            }
            names.addAll(own.keySet());
            return Collections.enumeration(names);
        }

        @Override
        public void setAttribute(String name, Object value) {
            if (isOwn(name)) {
                if (value == null) {
                    own.remove(name);
                } else {
                    own.put(name, value);
                }
                return;
            }
            super.setAttribute(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            if (isOwn(name)) {
                own.remove(name);
                return;
            }
            super.removeAttribute(name);
        }
    }
}
