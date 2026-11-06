package dev.escalated.ui;

import dev.escalated.plugins.PluginRegistry;
import dev.escalated.security.AccessControl;
import dev.escalated.security.AccessFeatures;
import dev.escalated.security.EscalatedAuthorization;
import dev.escalated.security.RoutePermissions;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

/**
 * The admin menu as data (ADR 2026-11-05-embeddable-admin), for a host that lists Escalated's
 * admin in its own sidebar: {@code escalated.navigation} while embedding is on, and
 * {@link #adminNavigation} for server-rendered layouts whether or not it is.
 *
 * <p>Each item is {@code {key, label, route, url, icon, position, active, badge, plugin,
 * children}}, sorted by position as the standalone sidebar orders them. Filtered server-side so a
 * sidebar never shows a link that would 403 or 404:
 * <ol>
 *   <li>nothing for a user who fails the admin gate (any signed-in user while the host replaces
 *       the gate);
 *   <li>an item whose page this host does not serve is dropped (Newsletters here: the newsletter
 *       admin is an API);
 *   <li>an access feature that is switched off drops its items (Roles without roles and
 *       permissions);
 *   <li>while roles and permissions is on, an item needs every permission its page requires
 *       ({@link RoutePermissions});
 *   <li>the active plugins' admin menu items, as the plugin contract filters them, are added.
 * </ol>
 */
public class AdminNavigation {

    /** Core items: key (also the icon key), label, route name, position. */
    static final List<Object[]> ITEMS = List.of(
            new Object[] {"reports", "Reports", "escalated.admin.reports", 10},
            new Object[] {"tickets", "Tickets", "escalated.admin.tickets.index", 20},
            new Object[] {"departments", "Departments", "escalated.admin.departments.index", 30},
            new Object[] {"sla-policies", "SLA Policies", "escalated.admin.sla-policies.index", 40},
            new Object[] {"knowledge-base", "Knowledge Base", "escalated.admin.kb-articles.index", 45},
            new Object[] {"escalation-rules", "Escalation Rules", "escalated.admin.escalation-rules.index", 50},
            new Object[] {"workflows", "Workflows", "escalated.admin.workflows.index", 53},
            new Object[] {"automations", "Automations", "escalated.admin.automations.index", 53.5},
            new Object[] {"webhooks", "Webhooks", "escalated.admin.webhooks.index", 54},
            new Object[] {"tags", "Tags", "escalated.admin.tags.index", 60},
            new Object[] {"custom-fields", "Custom Fields", "escalated.admin.custom-fields.index", 62},
            new Object[] {"statuses", "Statuses", "escalated.admin.statuses.index", 64},
            new Object[] {"canned-responses", "Canned Responses", "escalated.admin.canned-responses.index", 70},
            new Object[] {"macros", "Macros", "escalated.admin.macros.index", 80},
            new Object[] {"newsletters", "Newsletters", "escalated.admin.newsletters.index", 81},
            new Object[] {"skills", "Skills", "escalated.admin.skills.index", 82},
            new Object[] {"users", "Users", "escalated.admin.users.index", 83},
            new Object[] {"roles", "Roles", "escalated.admin.roles.index", 84},
            new Object[] {"capacity", "Capacity", "escalated.admin.capacity.index", 85},
            new Object[] {"business-hours", "Business Hours", "escalated.admin.business-hours.index", 86},
            new Object[] {"audit-log", "Audit Log", "escalated.admin.audit-log", 88},
            new Object[] {"api-tokens", "API Tokens", "escalated.admin.api-tokens.index", 90},
            new Object[] {"custom-objects", "Custom Objects", "escalated.admin.custom-objects.index", 92},
            new Object[] {"plugins", "Plugins", "escalated.admin.plugins.index", 95},
            new Object[] {"import", "Import", "escalated.admin.import.index", 97},
            new Object[] {"settings", "Settings", "escalated.admin.settings", 100});

    private final UiRoutes routes;
    private final EscalatedEmbed embed;
    private final EscalatedAuthorization authorization;
    private final AccessFeatures features;
    private final AccessControl access;
    private final Supplier<PluginRegistry> plugins;
    private final Supplier<RequestMappingHandlerMapping> mappings;
    private volatile List<PathPattern> pages;

    public AdminNavigation(UiRoutes routes, EscalatedEmbed embed, EscalatedAuthorization authorization,
                           AccessFeatures features, AccessControl access, Supplier<PluginRegistry> plugins,
                           Supplier<RequestMappingHandlerMapping> mappings) {
        this.routes = routes;
        this.embed = embed;
        this.authorization = authorization;
        this.features = features;
        this.access = access;
        this.plugins = plugins;
        this.mappings = mappings;
    }

    /** The menu for the signed-in user of the current security context. */
    public List<Map<String, Object>> adminNavigation(HttpServletRequest request) {
        return adminNavigation(SecurityContextHolder.getContext().getAuthentication(), request);
    }

    /** The menu for {@code user}; {@code active} is decided from {@code request}'s path (null: none). */
    public List<Map<String, Object>> adminNavigation(Authentication user, HttpServletRequest request) {
        boolean signedIn = user != null && user.isAuthenticated() && !(user instanceof AnonymousAuthenticationToken);
        if (!signedIn) {
            return new ArrayList<>();
        }
        if (!embed.replacesAdminGate() && !authorization.isAdmin(user)) {
            return new ArrayList<>();
        }
        String path = request == null ? null : EscalatedEmbed.currentPath(request);
        Set<String> granted = access.enforced() ? access.permissionsOf(user) : null;

        List<Map<String, Object>> items = new ArrayList<>();
        for (Object[] core : ITEMS) {
            String key = (String) core[0];
            String route = (String) core[2];
            if (!routes.all().containsKey(route)) {
                continue;
            }
            String url = routes.url(route);
            String standalone = embed.toStandalone(url);
            if (!served(standalone)) {
                continue;
            }
            if ("roles".equals(key) && !features.rolesPermissions()) {
                continue;
            }
            if (granted != null) {
                List<String> required = RoutePermissions.mapped("GET", standalone);
                if (required == null || !granted.containsAll(required)) {
                    continue;
                }
            }
            items.add(item(key, (String) core[1], route, url, key, (Number) core[3], path));
        }

        PluginRegistry registry = plugins.get();
        if (registry != null) {
            Object menu = registry.uiExtensions(user).get("menuItems");
            int index = 0;
            if (menu instanceof List<?> list) {
                for (Object entry : list) {
                    if (!(entry instanceof Map<?, ?> plugin) || !"admin".equals(plugin.get("section"))) {
                        continue;
                    }
                    Object target = plugin.get("route");
                    if (!(target instanceof String url) || url.isEmpty()) {
                        continue;
                    }
                    String slug = plugin.get("plugin") == null ? "native" : String.valueOf(plugin.get("plugin"));
                    Object position = plugin.get("position");
                    Map<String, Object> item = item("plugin:" + slug + ":" + index++,
                            plugin.get("label") == null ? "" : String.valueOf(plugin.get("label")), null,
                            embed.toEmbedded(url), plugin.get("icon") instanceof String icon ? icon : null,
                            position instanceof Number number ? number : 100, path);
                    item.put("badge", plugin.get("badge"));
                    item.put("plugin", plugin.get("plugin"));
                    items.add(item);
                }
            }
        }

        items.sort(Comparator.comparingDouble(item -> ((Number) item.get("position")).doubleValue()));
        return items;
    }

    private static Map<String, Object> item(String key, String label, String route, String url, String icon,
                                            Number position, String path) {
        String target = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        String trimmed = target.endsWith("/") ? target.substring(0, target.length() - 1) : target;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("route", route);
        item.put("url", url);
        item.put("icon", icon);
        item.put("position", position);
        item.put("active", path != null && (path.equals(target) || path.startsWith(trimmed + "/")));
        item.put("badge", null);
        item.put("plugin", null);
        item.put("children", new ArrayList<>());
        return item;
    }

    /** Whether a page answers GET at {@code path} on this host. */
    private boolean served(String path) {
        List<PathPattern> known = pages;
        if (known == null) {
            known = new ArrayList<>();
            RequestMappingHandlerMapping mapping = mappings.get();
            if (mapping != null) {
                for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
                    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                    PathPatternsRequestCondition patterns = info.getPathPatternsCondition();
                    if (patterns != null && (methods.isEmpty() || methods.contains(RequestMethod.GET))) {
                        known.addAll(patterns.getPatterns());
                    }
                }
            }
            pages = known;
        }
        PathContainer container = PathContainer.parsePath(path);
        return known.stream().anyMatch(pattern -> pattern.matches(container));
    }
}
