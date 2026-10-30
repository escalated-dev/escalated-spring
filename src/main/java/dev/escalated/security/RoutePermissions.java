package dev.escalated.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The permission each admin, agent and staff API route requires while roles &amp; permissions is
 * enforced (ADR 2026-10-29-configurable-access-features).
 *
 * <p>This is escalated-laravel's {@code src/Support/RoutePermissions.php}, keyed by HTTP method and
 * path instead of a route name. Entries are tried in order and the first match wins, so exact paths
 * come before wider patterns. In a pattern {@code *} is one path segment, {@code **} any number of
 * them and {@code {a,b}} either word. A staff route that matches nothing is refused, and a test
 * enumerates the handler mappings so a new route cannot ship without an entry here.
 *
 * <p>Every slug is one escalated-laravel's {@code PermissionSeeder} seeds; none are invented here.
 * An empty list means "staff, no particular permission": personal screens, and plugin pages and
 * endpoints, which the plugin guards with its own capability.
 */
public final class RoutePermissions {

    /** One map entry: methods ({@code *} or {@code GET|POST}), a path pattern and the slugs. */
    public record Entry(String methods, String pattern, List<String> slugs) {
    }

    /** Statuses whose change needs {@code ticket.close} as well as {@code ticket.edit}. */
    public static final Set<String> CLOSING_STATUSES = Set.of("resolved", "closed", "reopened");

    /** Bulk actions and the permission each needs. */
    public static final Map<String, List<String>> BULK_ACTIONS = Map.of(
            "status", List.of("ticket.edit"),
            "priority", List.of("ticket.edit"),
            "assign", List.of("ticket.assign"),
            "tags", List.of("ticket.edit"),
            "department", List.of("ticket.edit"),
            "delete", List.of("ticket.delete"));

    /** The path prefixes whose routes are staff routes, and so must be mapped. */
    public static final List<String> STAFF_PREFIXES = List.of(
            "/escalated/admin", "/escalated/agent", "/escalated/api/admin", "/escalated/api/agent",
            "/escalated/api/plugins");

    private static final String A = "/escalated/admin";
    private static final String G = "/escalated/agent";
    private static final String STAFF = "/escalated/{admin,agent}";
    private static final String API_A = "/escalated/api/admin";
    private static final String API_G = "/escalated/api/agent";
    /** An admin page and its admin API twin. */
    private static final String BOTH = "/{escalated/admin,escalated/api/admin}";
    private static final List<String> VIEW = List.of("ticket.view");
    private static final List<String> EDIT = List.of("ticket.edit");

    private static final List<Entry> ENTRIES = build();

    private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();

    private RoutePermissions() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** Whether {@code path} is under one of the staff prefixes. */
    public static boolean isStaffPath(String path) {
        String route = normalize(path);
        for (String prefix : STAFF_PREFIXES) {
            if (route.equals(prefix) || route.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /** The slugs the route needs before looking at the request body, or null when it is unmapped. */
    public static List<String> mapped(String method, String path) {
        String verb = "HEAD".equalsIgnoreCase(method) ? "GET" : method.toUpperCase(Locale.ROOT);
        String route = normalize(path);
        for (Entry entry : ENTRIES) {
            if (!"*".equals(entry.methods()) && !List.of(entry.methods().split("\\|")).contains(verb)) {
                continue;
            }
            if (regex(entry.pattern()).matcher(route).matches()) {
                return new ArrayList<>(entry.slugs());
            }
        }
        return null;
    }

    /**
     * The slugs a request needs, or null when its route is not mapped (the caller refuses it).
     * Body-decided actions are checked per action: a status change to resolved, closed or reopened
     * (or any solved custom status) also needs {@code ticket.close}, a bulk action needs its own
     * permission, and an internal reply needs {@code reply.create_internal}.
     *
     * @param input the request's submitted values (JSON body or form fields)
     * @param solved whether a status value names a custom status in the solved category
     */
    public static List<String> required(String method, String path, Map<String, Object> input,
                                        Function<String, Boolean> solved) {
        List<String> required = mapped(method, path);
        if (required == null) {
            return null;
        }
        String route = normalize(path);
        Map<String, Object> body = input == null ? Map.of() : input;
        if (regex(STAFF + "/tickets/bulk").matcher(route).matches()) {
            String action = body.get("action") == null ? "" : String.valueOf(body.get("action"));
            required.addAll(BULK_ACTIONS.getOrDefault(action, List.of()));
            if ("status".equals(action)) {
                required.addAll(closing(body.get("value"), solved));
            }
        } else if (regex(API_G + "/tickets/*/replies").matcher(route).matches() && "POST".equalsIgnoreCase(method)
                && truthy(body.get("internal"))) {
            required = new ArrayList<>(List.of("reply.create_internal"));
        } else if (required.contains("ticket.edit")) {
            required.addAll(closing(body.get("status"), solved));
        }
        return new ArrayList<>(new LinkedHashSet<>(required));
    }

    /** {@code /a//b/} is {@code /a/b}: the form paths are compared in. */
    public static String normalize(String path) {
        String joined = ("/" + (path == null ? "" : path)).replaceAll("/+", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }

    private static List<String> closing(Object value, Function<String, Boolean> solved) {
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        String status = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (CLOSING_STATUSES.contains(status)) {
            return List.of("ticket.close");
        }
        return solved != null && Boolean.TRUE.equals(solved.apply(status)) ? List.of("ticket.close") : List.of();
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        String text = value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return text.equals("true") || text.equals("1");
    }

    static Pattern regex(String pattern) {
        return COMPILED.computeIfAbsent(pattern, RoutePermissions::compile);
    }

    private static Pattern compile(String pattern) {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                source.append(".+");
                i++;
            } else if (c == '*') {
                source.append("[^/]+");
            } else if (c == '{') {
                int end = pattern.indexOf('}', i);
                List<String> parts = new ArrayList<>();
                for (String part : pattern.substring(i + 1, end).split(",")) {
                    parts.add(Pattern.quote(part));
                }
                source.append("(?:").append(String.join("|", parts)).append(')');
                i = end;
            } else {
                source.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile("^" + source + "$");
    }

    private static Entry e(String methods, String pattern, String... slugs) {
        return new Entry(methods, pattern, List.of(slugs));
    }

    private static Entry e(String methods, String pattern, List<String> slugs) {
        return new Entry(methods, pattern, slugs);
    }

    /**
     * Index and reads need {@code {group}.view}; the create and edit forms and every write need
     * {@code {group}.manage}, as Laravel's {@code resource()} helper.
     */
    private static List<Entry> resource(String base, String group) {
        List<String> view = List.of(group + ".view");
        List<String> manage = List.of(group + ".manage");
        return List.of(
                e("GET", base + "/create", manage),
                e("GET", base + "/new", manage),
                e("GET", base + "/*/edit", manage),
                e("GET", base, view),
                e("GET", base + "/**", view),
                e("*", base, manage),
                e("*", base + "/**", manage));
    }

    private static List<Entry> build() {
        List<Entry> map = new ArrayList<>();

        // ---- Personal: every staff user's own two-factor enrolment -------------
        map.add(e("*", A + "/settings/two-factor", List.of()));
        map.add(e("*", A + "/settings/two-factor/**", List.of()));

        // ---- Tickets: the admin and agent pages ------------------------------
        map.add(e("GET", G, VIEW));
        map.add(e("POST", STAFF + "/tickets/bulk", VIEW)); // plus each action's, see required()
        map.add(e("GET", STAFF + "/tickets", VIEW));
        map.add(e("GET", STAFF + "/tickets/*", VIEW));
        map.add(e("POST", STAFF + "/tickets/*/{status,priority,tags,department,snooze,unsnooze}", EDIT));
        map.add(e("POST", STAFF + "/tickets/*/actions/*", EDIT));
        map.add(e("POST", STAFF + "/tickets/*/assign", "ticket.assign"));
        map.add(e("POST", STAFF + "/tickets/*/follow", VIEW));
        map.add(e("POST", STAFF + "/tickets/*/macro", "macro.view", "ticket.edit"));
        map.add(e("POST", STAFF + "/tickets/*/reply", "reply.create"));
        map.add(e("POST", STAFF + "/tickets/*/note", "reply.create_internal"));
        map.add(e("*", A + "/saved-views", VIEW));
        map.add(e("*", A + "/saved-views/**", VIEW));
        map.add(e("POST", G + "/chat/**", "reply.create"));

        // ---- Tickets: the staff API -------------------------------------------
        map.add(e("GET", API_A + "/tickets", VIEW));
        map.add(e("GET", API_A + "/tickets/**", VIEW));
        map.add(e("POST", API_A + "/tickets", "ticket.create"));
        map.add(e("POST", API_A + "/tickets/*/split", "ticket.create"));
        map.add(e("POST", API_A + "/tickets/*/merge", "ticket.merge"));
        map.add(e("POST", API_A + "/tickets/*/assign", "ticket.assign"));
        map.add(e("DELETE", API_A + "/tickets/*", "ticket.delete"));
        map.add(e("PUT", API_A + "/tickets/*", EDIT));
        map.add(e("*", API_A + "/tickets/*/{status,snooze,tags,department,subjects}", EDIT));
        map.add(e("*", API_A + "/tickets/*/{tags,subjects}/*", EDIT));
        map.add(e("GET", API_G + "/tickets", VIEW));
        map.add(e("GET", API_G + "/tickets/**", VIEW));
        map.add(e("POST", API_G + "/tickets/*/actions/*", EDIT));
        map.add(e("POST", API_G + "/tickets/*/replies", "reply.create")); // internal: reply.create_internal
        map.add(e("POST", API_G + "/tickets/*/macro/*", "macro.view", "ticket.edit"));
        map.add(e("POST", API_G + "/tickets/*/side-conversations", "reply.create"));
        map.add(e("POST", API_G + "/tickets/*/links", EDIT));
        map.add(e("*", API_G + "/saved-views", VIEW));
        map.add(e("*", API_G + "/saved-views/**", VIEW));
        map.add(e("GET", API_G + "/canned-responses", VIEW));
        map.add(e("*", API_G + "/canned-responses", "macro.create"));
        map.add(e("*", API_G + "/canned-responses/**", "macro.create"));
        map.add(e("GET", API_G + "/chat/**", VIEW));
        map.add(e("POST", API_G + "/chat/**", "reply.create"));

        // ---- Reports --------------------------------------------------------------
        map.add(e("GET", BOTH + "/reports", "report.view"));
        map.add(e("GET", BOTH + "/reports/**", "report.view"));

        // ---- Knowledge base (pages and JSON) ------------------------------------------
        map.add(e("GET", A + "/kb-articles/create", "kb.create"));
        map.add(e("GET", A + "/kb-articles/*/edit", "kb.edit"));
        map.add(e("GET", A + "/{kb-articles,kb-categories}", "kb.view"));
        map.add(e("GET", API_A + "/kb/**", "kb.view"));
        map.add(e("POST", A + "/kb-articles", "kb.create"));
        map.add(e("POST", API_A + "/kb/articles", "kb.create"));
        map.add(e("POST", API_A + "/kb/articles/*/publish", "kb.publish"));
        map.add(e("PUT", A + "/kb-articles/*", "kb.edit"));
        map.add(e("POST", A + "/kb-categories", "kb.edit"));
        map.add(e("POST", API_A + "/kb/categories", "kb.edit"));
        map.add(e("PUT", A + "/kb-categories/*", "kb.edit"));
        map.add(e("DELETE", A + "/{kb-articles,kb-categories}/*", "kb.delete"));

        // ---- Departments ----------------------------------------------------------------
        for (String base : List.of(A + "/departments", API_A + "/departments")) {
            map.add(e("GET", base + "/create", "department.create"));
            map.add(e("GET", base + "/*/edit", "department.edit"));
            map.add(e("GET", base, "department.view"));
            map.add(e("GET", base + "/*", "department.view"));
            map.add(e("POST", base, "department.create"));
            map.add(e("PUT", base + "/*", "department.edit"));
            map.add(e("DELETE", base + "/*", "department.delete"));
        }

        // ---- Macros: anyone who may create one may save it; edits manage -----------------
        map.add(e("GET", A + "/macros", "macro.view"));
        map.add(e("POST", A + "/macros", "macro.create"));
        map.add(e("*", A + "/macros/**", "macro.manage"));

        // ---- Custom objects: schemas manage, records data --------------------------------
        for (String base : List.of(A + "/custom-objects", API_A + "/custom-objects")) {
            map.add(e("GET", base + "/*/records", "custom_object.view"));
            map.add(e("*", base + "/*/records", "custom_object.data"));
            map.add(e("*", base + "/*/records/**", "custom_object.data"));
            map.addAll(resource(base, "custom_object"));
        }

        // ---- Users, agents, audit ------------------------------------------------------------
        map.add(e("GET", BOTH + "/users", "user.view"));
        map.add(e("*", BOTH + "/users/*/role", "user.manage"));
        map.add(e("GET", BOTH + "/{audit-log,audit-logs}",
                "audit.view"));

        // ---- Roles (GET permissions feeds the role form) ---------------------------------------
        map.add(e("GET", API_A + "/roles/permissions", "role.view"));
        map.addAll(resource(A + "/roles", "role"));
        map.addAll(resource(API_A + "/roles", "role"));

        // ---- Plugins: the screens; plugin pages and endpoints are the plugin's -------------------
        map.add(e("GET", A + "/plugins", "plugin.view"));
        map.add(e("GET", A + "/plugins/*/**", List.of()));
        map.add(e("*", A + "/plugins", "plugin.manage"));
        map.add(e("*", A + "/plugins/**", "plugin.manage"));
        map.add(e("*", "/escalated/api/plugins/*", List.of()));
        map.add(e("*", "/escalated/api/plugins/*/**", List.of()));

        // ---- Newsletters (ADR 2026-05-19-newsletter-system); sending also needs newsletters.send --
        map.add(e("*", API_A + "/newsletters", "newsletters.manage"));
        map.add(e("*", API_A + "/newsletters/**", "newsletters.manage"));

        // ---- Admin resources: index and reads need *.view, the rest *.manage ----------------------
        map.addAll(resource(A + "/sla-policies", "sla"));
        map.addAll(resource(A + "/business-hours", "sla"));
        map.addAll(resource(A + "/escalation-rules", "escalation"));
        for (String base : List.of(A, API_A)) {
            map.addAll(resource(base + "/automations", "automation"));
            map.addAll(resource(base + "/workflows", "automation"));
            map.addAll(resource(base + "/custom-fields", "custom_field"));
            map.addAll(resource(base + "/webhooks", "webhook"));
            map.addAll(resource(base + "/skills", "user"));
        }
        map.addAll(resource(A + "/tags", "tag"));
        map.addAll(resource(A + "/statuses", "settings"));
        map.addAll(resource(A + "/capacity", "user"));
        map.addAll(resource(API_A + "/agents", "user"));
        map.addAll(resource(A + "/api-tokens", "api_token"));
        map.addAll(resource(API_A + "/tokens", "api_token"));
        map.addAll(resource(A + "/canned-responses", "macro"));

        // ---- Settings and imports --------------------------------------------------------------
        for (String base : List.of(A, API_A)) {
            map.add(e("*", base + "/import", "settings.manage"));
            map.add(e("*", base + "/import/**", "settings.manage"));
            map.add(e("GET", base + "/settings", "settings.view"));
            map.add(e("GET", base + "/settings/**", "settings.view"));
            map.add(e("*", base + "/settings", "settings.manage"));
            map.add(e("*", base + "/settings/**", "settings.manage"));
        }

        return Collections.unmodifiableList(map);
    }
}
