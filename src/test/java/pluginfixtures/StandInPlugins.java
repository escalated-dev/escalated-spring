package pluginfixtures;

import dev.escalated.plugins.EscalatedPlugin;
import dev.escalated.plugins.PluginAction;
import dev.escalated.plugins.PluginCapabilities;
import dev.escalated.plugins.PluginContext;
import dev.escalated.plugins.PluginFilter;
import dev.escalated.plugins.PluginManifest;
import dev.escalated.plugins.PluginRequest;
import dev.escalated.plugins.PluginResponse;
import dev.escalated.plugins.PluginRoute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Native stand-in plugins for the plugin tests, and what they saw. Outside {@code dev.escalated} so
 * that Escalated's component scan never registers them in other tests' contexts.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StandInPlugins {

    /** Everything the stand-ins were called with, in order: "{plugin} {what}". */
    public static final List<String> CALLS = Collections.synchronizedList(new ArrayList<>());

    /** Every action event a stand-in received, as {plugin, hook, event}. */
    public static final List<Map<String, Object>> EVENTS = Collections.synchronizedList(new ArrayList<>());

    public static void reset() {
        CALLS.clear();
        EVENTS.clear();
    }

    public static List<Map<String, Object>> events(String plugin, String hook) {
        synchronized (EVENTS) {
            return EVENTS.stream().filter(e -> plugin.equals(e.get("plugin")) && hook.equals(e.get("hook"))).toList();
        }
    }

    private static PluginAction record(String plugin, String hook) {
        return (event, ctx) -> {
            CALLS.add(plugin + " " + hook);
            Map<String, Object> seen = new LinkedHashMap<>();
            seen.put("plugin", plugin);
            seen.put("hook", hook);
            seen.put("event", event);
            EVENTS.add(seen);
        };
    }

    private static Map<String, PluginAction> recordAll(String plugin, String... hooks) {
        Map<String, PluginAction> actions = new LinkedHashMap<>();
        for (String hook : hooks) {
            actions.put(hook, record(plugin, hook));
        }
        return actions;
    }

    private static final String[] HOOKS = {
        "plugin.activated", "plugin.deactivated", "plugin.uninstalling", "ticket.created", "reply.created",
        "note.created", "ticket.status_changed", "ticket.assigned", "custom.ping",
        // Legacy names: a stand-in listening for them must never hear them.
        "ticket.replied", "internal_note.added",
    };

    /** {@code acme/billing}: pages, slots, widgets, endpoints, a webhook, actions and a filter. */
    @Bean
    public EscalatedPlugin billingPlugin() {
        return new EscalatedPlugin() {
            @Override
            public PluginManifest manifest() {
                return PluginManifest.of("acme/billing", "2.1.0")
                        .withDescription("Invoices on tickets")
                        .withAuthor("Acme", "https://acme.example")
                        .withPages(
                                new PluginManifest.Page("/settings", "Settings", null, null,
                                        new PluginManifest.Menu("Billing", null, 30, "credit-card")),
                                new PluginManifest.Page("agent-view", "AgentView", "agent", null,
                                        new PluginManifest.Menu("Invoices", "agent", null, null)),
                                new PluginManifest.Page("secret", "Secret", null, "billing.secret",
                                        new PluginManifest.Menu("Secret", null, null, null)),
                                new PluginManifest.Page("plain", "Plain", null, null, null))
                        .withComponents(new PluginManifest.SlotComponent("ticket.show", "sidebar", "InvoiceCard",
                                Map.of("compact", true), null, null))
                        .withWidgets(new PluginManifest.Widget("RevenueWidget", "Revenue", null, 5, null));
            }

            @Override
            public void onActivate(PluginContext ctx) {
                CALLS.add("acme/billing onActivate");
            }

            @Override
            public void onDeactivate(PluginContext ctx) {
                CALLS.add("acme/billing onDeactivate");
            }

            @Override
            public Map<String, PluginAction> actions() {
                return recordAll("acme/billing", HOOKS);
            }

            @Override
            public List<PluginFilter> filters() {
                return List.of(new PluginFilter("subject.filter", 20, (value, ctx) -> value + "|billing"));
            }

            @Override
            public List<PluginRoute> endpoints() {
                return List.of(
                        PluginRoute.of("GET", "/Settings", (req, ctx) -> Map.of("greeting", "hello from " + ctx.plugin())),
                        PluginRoute.of("GET", "/items/:id", (req, ctx) -> echo(req)),
                        PluginRoute.of("POST", "/items/:id", (req, ctx) -> echo(req)),
                        PluginRoute.of("GET", "/boom", (req, ctx) -> {
                            throw new IllegalStateException("secret failure detail");
                        }),
                        PluginRoute.of("GET", "/envelope", (req, ctx) ->
                                new PluginResponse(201, Map.of("X-Plugin", "yes"), Map.of("ok", true), false)),
                        PluginRoute.of("GET", "/bad-header", (req, ctx) ->
                                new PluginResponse(200, Map.of("Set-Cookie", "a=b"), Map.of(), false)),
                        PluginRoute.of("POST", "/tickets", (req, ctx) -> ctx.tickets().create(record(req.body()))),
                        new PluginRoute("GET", "/secure", "billing.secret", (req, ctx) -> Map.of("secret", true)));
            }

            @Override
            public List<PluginRoute> webhooks() {
                return List.of(PluginRoute.of("POST", "/hook", (req, ctx) -> echo(req)));
            }
        };
    }

    /** {@code acme/second}: a throwing onActivate, a throwing action, an early filter and a throwing one. */
    @Bean
    public EscalatedPlugin secondPlugin() {
        return new EscalatedPlugin() {
            @Override
            public PluginManifest manifest() {
                return PluginManifest.of("acme/second", "1.0.0");
            }

            @Override
            public void onActivate(PluginContext ctx) {
                CALLS.add("acme/second onActivate");
                throw new IllegalStateException("onActivate failed");
            }

            @Override
            public Map<String, PluginAction> actions() {
                Map<String, PluginAction> actions = new LinkedHashMap<>(recordAll("acme/second", HOOKS));
                actions.put("ticket.created", (event, ctx) -> {
                    CALLS.add("acme/second ticket.created");
                    throw new IllegalStateException("action failed");
                });
                return actions;
            }

            @Override
            public List<PluginFilter> filters() {
                return List.of(
                        new PluginFilter("subject.filter", 5, (value, ctx) -> value + "|second"),
                        new PluginFilter("subject.filter", 15, (value, ctx) -> {
                            throw new IllegalStateException("filter failed");
                        }));
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> record(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> echo(PluginRequest req) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method", req.method());
        out.put("path", req.path());
        out.put("params", req.params());
        out.put("query", req.query());
        out.put("body", req.body());
        out.put("headers", req.headers());
        out.put("raw", new String(req.rawBody(), java.nio.charset.StandardCharsets.UTF_8));
        return out;
    }

    /** A host's capability rule: only users whose name starts with "boss" hold billing.secret. */
    @Bean
    public PluginCapabilities testCapabilities() {
        return (user, capability) -> !"billing.secret".equals(capability) || user.getName().startsWith("boss");
    }
}
