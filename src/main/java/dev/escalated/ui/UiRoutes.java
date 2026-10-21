package dev.escalated.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The named routes the shared pages ask for with Ziggy's {@code route(name, params)}.
 *
 * <p>Laravel hosts get {@code route()} from Ziggy. Here {@link #script()} defines a compatible
 * {@code window.route} from this table (the built-in root page includes it; a custom root view gets
 * it as {@code routesScript}). A name with no route here answers {@code '#'} and a console
 * warning, so a page whose secondary screens this port does not serve yet still renders.
 *
 * <p>A host adds or moves a route with {@link #register}.
 */
public class UiRoutes {

    /** Where every Escalated page lives. The mappings are fixed at {@code /escalated}. */
    public static final String PREFIX = "/escalated";

    private static final Pattern PARAMETER = Pattern.compile("\\{(\\w+)\\??}");

    private final Map<String, String> routes = new LinkedHashMap<>();

    public UiRoutes() {
        // Agent
        register("escalated.agent.dashboard", "/agent");
        register("escalated.agent.tickets.index", "/agent/tickets");
        register("escalated.agent.tickets.show", "/agent/tickets/{ticket}");
        register("escalated.agent.tickets.reply", "/agent/tickets/{ticket}/reply");
        register("escalated.agent.tickets.note", "/agent/tickets/{ticket}/note");
        register("escalated.agent.tickets.assign", "/agent/tickets/{ticket}/assign");
        register("escalated.agent.tickets.status", "/agent/tickets/{ticket}/status");
        register("escalated.agent.tickets.priority", "/agent/tickets/{ticket}/priority");
        register("escalated.agent.tickets.tags", "/agent/tickets/{ticket}/tags");
        register("escalated.agent.tickets.department", "/agent/tickets/{ticket}/department");
        register("escalated.agent.tickets.macro", "/agent/tickets/{ticket}/macro");
        register("escalated.agent.tickets.follow", "/agent/tickets/{ticket}/follow");
        register("escalated.agent.tickets.custom-action", "/agent/tickets/{ticket}/actions/{action}");
        // Customer
        register("escalated.customer.tickets.index", "");
        register("escalated.customer.tickets.create", "/create");
        register("escalated.customer.tickets.store", "");
        register("escalated.customer.tickets.show", "/{ticket}");
        register("escalated.customer.tickets.reply", "/{ticket}/reply");
        register("escalated.customer.tickets.close", "/{ticket}/close");
        register("escalated.customer.tickets.reopen", "/{ticket}/reopen");
        register("escalated.customer.tickets.rate", "/{ticket}/rate");
        // Admin
        register("escalated.admin.settings", "/admin/settings");
        register("escalated.admin.settings.update", "/admin/settings");
        resource("workflows", "workflow", "/admin/workflows/{workflow}");
        register("escalated.admin.workflows.toggle", "/admin/workflows/{workflow}/toggle");
        register("escalated.admin.workflows.reorder", "/admin/workflows/reorder");
        resource("automations", "automation", "/admin/automations/{automation}/edit");
        resource("departments", "department", "/admin/departments/{department}/edit");
        register("escalated.admin.tags.index", "/admin/tags");
        register("escalated.admin.tags.store", "/admin/tags");
        register("escalated.admin.tags.update", "/admin/tags/{tag}");
        register("escalated.admin.tags.destroy", "/admin/tags/{tag}");
        register("escalated.admin.tickets.index", "/admin/tickets");
        register("escalated.admin.tickets.bulk", "/admin/tickets/bulk");
        register("escalated.admin.tickets.show", "/admin/tickets/{ticket}");
        for (String action : new String[] {"reply", "note", "assign", "status", "priority", "tags", "department",
                "macro", "follow", "snooze", "unsnooze"}) {
            register("escalated.admin.tickets." + action, "/admin/tickets/{ticket}/" + action);
        }
        register("escalated.admin.saved-views.index", "/admin/saved-views");
        register("escalated.admin.saved-views.store", "/admin/saved-views");
        register("escalated.admin.saved-views.update", "/admin/saved-views/{savedView}");
        register("escalated.admin.saved-views.destroy", "/admin/saved-views/{savedView}");
        for (String plural : new String[] {"macros", "canned-responses"}) {
            register("escalated.admin." + plural + ".index", "/admin/" + plural);
            register("escalated.admin." + plural + ".store", "/admin/" + plural);
            register("escalated.admin." + plural + ".update", "/admin/" + plural + "/{id}");
            register("escalated.admin." + plural + ".destroy", "/admin/" + plural + "/{id}");
        }
        resource("webhooks", "webhook", "/admin/webhooks/{webhook}/edit");
        register("escalated.admin.webhooks.deliveries", "/admin/webhooks/{webhook}/deliveries");
        register("escalated.admin.webhooks.retry", "/admin/webhooks/deliveries/{delivery}/retry");
        register("escalated.admin.users.index", "/admin/users");
        register("escalated.admin.users.role", "/admin/users/{user}/role");
        resource("roles", "role", "/admin/roles/{role}/edit");
        register("escalated.admin.api-tokens.index", "/admin/api-tokens");
        register("escalated.admin.api-tokens.store", "/admin/api-tokens");
        register("escalated.admin.api-tokens.update", "/admin/api-tokens/{id}");
        register("escalated.admin.api-tokens.destroy", "/admin/api-tokens/{id}");
        resource("kb-articles", "kb_article", "/admin/kb-articles/{kb_article}/edit");
        register("escalated.admin.kb-categories.index", "/admin/kb-categories");
        register("escalated.admin.kb-categories.store", "/admin/kb-categories");
        register("escalated.admin.kb-categories.update", "/admin/kb-categories/{kb_category}");
        register("escalated.admin.kb-categories.destroy", "/admin/kb-categories/{kb_category}");
        register("escalated.customer.kb.index", "/kb");
        register("escalated.customer.kb.show", "/kb/{slug}");
        register("escalated.customer.kb.feedback", "/kb/{slug}/feedback");
        register("escalated.attachments.download", "/attachments/{attachment}/download");
    }

    /** Laravel's resource routes (less {@code show}), with the edit page at {@code edit}. */
    private void resource(String plural, String parameter, String edit) {
        String base = "/admin/" + plural;
        String member = base + "/{" + parameter + "}";
        register("escalated.admin." + plural + ".index", base);
        register("escalated.admin." + plural + ".create", base + "/create");
        register("escalated.admin." + plural + ".store", base);
        register("escalated.admin." + plural + ".edit", edit);
        register("escalated.admin." + plural + ".update", member);
        register("escalated.admin." + plural + ".destroy", member);
    }

    /** Adds or replaces a route. {@code uri} is relative to {@link #PREFIX} unless it starts with {@code //}. */
    public final void register(String name, String uri) {
        routes.put(name, uri.startsWith("//") ? uri.substring(1) : PREFIX + uri);
    }

    public Map<String, String> all() {
        return Collections.unmodifiableMap(routes);
    }

    /** The URL of {@code name}, its parameters filled in order. */
    public String url(String name, Object... parameters) {
        String uri = routes.get(name);
        if (uri == null) {
            throw new IllegalArgumentException("No Escalated route named " + name);
        }
        Matcher matcher = PARAMETER.matcher(uri);
        StringBuilder url = new StringBuilder();
        int index = 0;
        while (matcher.find()) {
            Object value = index < parameters.length ? parameters[index] : null;
            index++;
            String encoded = value == null ? "" : URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            matcher.appendReplacement(url, Matcher.quoteReplacement(encoded));
        }
        matcher.appendTail(url);
        return url.toString();
    }

    /**
     * JavaScript defining {@code window.route} from this table, unless the host already has a
     * {@code route()} (Ziggy). Parameters work as in Ziggy: one value fills the first placeholder,
     * an array fills them in order, an object fills them by name and its other keys become the
     * query string.
     */
    public String script() {
        String table = Inertia.toJson(routes).replace("<", "\\u003c").replace(">", "\\u003e");
        return "(function(){var R=" + table + ";"
                + "function route(name,params){var uri=R[name];"
                + "if(uri===undefined){if(window.console){console.warn('[escalated] no route named '+name+' on this host');}return '#';}"
                + "var keys=[];uri.replace(/\\{(\\w+)\\??\\}/g,function(m,k){keys.push(k);return m;});"
                + "var values={},rest={};"
                + "if(params!==undefined&&params!==null){"
                + "if(typeof params!=='object'){values[keys[0]]=params;}"
                + "else if(Array.isArray(params)){params.forEach(function(v,i){values[keys[i]]=v;});}"
                + "else{Object.keys(params).forEach(function(k){if(keys.indexOf(k)>=0){values[k]=params[k];}else{rest[k]=params[k];}});}}"
                + "var url=uri.replace(/\\{(\\w+)\\??\\}/g,function(m,k){var v=values[k];"
                + "if(v!==null&&typeof v==='object'){v=v.reference!==undefined?v.reference:v.id;}"
                + "return v===undefined||v===null?'':encodeURIComponent(v);});"
                + "var q=Object.keys(rest).filter(function(k){return rest[k]!==undefined&&rest[k]!==null&&rest[k]!=='';})"
                + ".map(function(k){return encodeURIComponent(k)+'='+encodeURIComponent(rest[k]);}).join('&');"
                + "return q?url+'?'+q:url;}"
                + "route.routes=R;route.has=function(n){return R[n]!==undefined;};"
                + "if(typeof window.route!=='function'||window.route.name==='escalatedRouteShimMissing'){window.route=route;}"
                + "})();";
    }
}
