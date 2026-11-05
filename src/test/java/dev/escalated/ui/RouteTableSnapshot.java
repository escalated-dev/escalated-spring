package dev.escalated.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every Escalated route a context serves, as data: each handler mapping under {@code /escalated}
 * (patterns, methods, handler), the security chain that guards it and that chain's filters, what
 * its gate decides for an anonymous visitor, a signed-in user, an agent and an admin; the servlet
 * filters Escalated registers; and the {@code route()} name table.
 *
 * <p>The embeddable admin is off by default, and with it off this must not change. The snapshot
 * in {@code src/test/resources/embed/default-routes.json} was taken from the code before the
 * embeddable admin existed.
 */
final class RouteTableSnapshot {

    private static final List<Map.Entry<String, Supplier<Authentication>>> CALLERS = List.of(
            Map.entry("anonymous", () -> new AnonymousAuthenticationToken("key", "anonymous",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))),
            Map.entry("user", () -> UsernamePasswordAuthenticationToken.authenticated(
                    "nobody-snapshot@example.com", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"))),
            Map.entry("agent", () -> UsernamePasswordAuthenticationToken.authenticated(
                    "agent-snapshot@example.com", "n/a", AuthorityUtils.createAuthorityList("ROLE_ESCALATED_AGENT"))),
            Map.entry("admin", () -> UsernamePasswordAuthenticationToken.authenticated(
                    "admin-snapshot@example.com", "n/a", AuthorityUtils.createAuthorityList("ROLE_ESCALATED_ADMIN"))));

    private RouteTableSnapshot() {
    }

    static Map<String, Object> of(ApplicationContext context) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("routes", routes(context));
        snapshot.put("filters", filters(context));
        snapshot.put("names", new TreeMap<>(context.getBean(UiRoutes.class).all()));
        return snapshot;
    }

    private static List<Map<String, Object>> routes(ApplicationContext context) {
        RequestMappingHandlerMapping mapping =
                context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        FilterChainProxy security = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
        List<Map<String, Object>> routes = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            List<String> methods = info.getMethodsCondition().getMethods().stream()
                    .map(RequestMethod::name).sorted().toList();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/escalated")) {
                    continue;
                }
                Map<String, Object> route = new LinkedHashMap<>();
                route.put("path", pattern);
                route.put("methods", methods);
                route.put("handler", entry.getValue().getBeanType().getName() + "#" + entry.getValue().getMethod().getName());
                String method = methods.isEmpty() ? "GET" : methods.get(0);
                route.put("guards", guards(security, method, sample(pattern)));
                routes.add(route);
            }
        }
        routes.sort(Comparator.comparing((Map<String, Object> route) -> (String) route.get("path"))
                .thenComparing(route -> String.valueOf(route.get("methods")))
                .thenComparing(route -> (String) route.get("handler")));
        return routes;
    }

    /** The chain a request to {@code path} goes through, its filters, and its gate's decisions. */
    private static Map<String, Object> guards(FilterChainProxy security, String method, String path) {
        Map<String, Object> guards = new LinkedHashMap<>();
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        SecurityFilterChain chain = security.getFilterChains().stream()
                .filter(candidate -> candidate.matches(request)).findFirst().orElse(null);
        if (chain == null) {
            guards.put("chain", null);
            return guards;
        }
        guards.put("chain", chain instanceof DefaultSecurityFilterChain defaults
                ? defaults.getRequestMatcher().toString() : chain.getClass().getName());
        guards.put("filters", chain.getFilters().stream().map(filter -> filter.getClass().getSimpleName()).toList());
        AuthorizationManager<jakarta.servlet.http.HttpServletRequest> gate = chain.getFilters().stream()
                .filter(AuthorizationFilter.class::isInstance).map(AuthorizationFilter.class::cast)
                .map(AuthorizationFilter::getAuthorizationManager).findFirst().orElse(null);
        if (gate != null) {
            Map<String, String> decisions = new LinkedHashMap<>();
            for (Map.Entry<String, Supplier<Authentication>> caller : CALLERS) {
                AuthorizationResult result = gate.authorize(caller.getValue(), request);
                decisions.put(caller.getKey(), result == null ? "abstain" : result.isGranted() ? "grant" : "deny");
            }
            guards.put("gate", decisions);
        }
        return guards;
    }

    /** A concrete path for {@code pattern}: each variable is {@code 1}, {@code **} is one segment. */
    static String sample(String pattern) {
        return pattern.replaceAll("\\{[^}/]+}", "1").replace("/**", "/x");
    }

    private static List<Map<String, Object>> filters(ApplicationContext context) {
        List<Map<String, Object>> filters = new ArrayList<>();
        for (Map.Entry<String, AbstractFilterRegistrationBean> entry
                : new TreeMap<>(context.getBeansOfType(AbstractFilterRegistrationBean.class)).entrySet()) {
            AbstractFilterRegistrationBean<?> registration = entry.getValue();
            String type = registration.getFilter() == null ? "?" : registration.getFilter().getClass().getName();
            if (!type.startsWith("dev.escalated") && !entry.getKey().contains("escalated")
                    && !entry.getKey().contains("securityFilterChain")) {
                continue;
            }
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("bean", entry.getKey());
            filter.put("filter", type);
            filter.put("enabled", registration.isEnabled());
            filter.put("urlPatterns", registration.getUrlPatterns().stream().sorted().toList());
            filter.put("order", registration.getOrder());
            filters.add(filter);
        }
        return filters;
    }
}
