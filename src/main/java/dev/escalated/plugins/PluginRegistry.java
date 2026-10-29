package dev.escalated.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import dev.escalated.events.EventNames;
import dev.escalated.models.PluginActivation;
import dev.escalated.repositories.PluginActivationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The plugin registry: the registered plugins (native {@link EscalatedPlugin} beans, then the SDK
 * plugins the runtime reports), their activation in {@code escalated_plugins}, their lifecycle,
 * and the dispatch of actions, filters, pages, endpoints and webhooks to the active ones. Inactive
 * plugins are never called.
 */
public class PluginRegistry implements DisposableBean {

    /** A native plugin, registered as a bean. */
    public static final String SOURCE_REGISTERED = "registered";

    /**
     * An SDK plugin installed with npm. The name is the contract's, historical: any package manager.
     * The admin screen refuses to delete it; the package manager removes it.
     */
    public static final String SOURCE_COMPOSER = "composer";

    /** Fired to every active plugin on activation, deactivation and uninstall, with {plugin, slug}. */
    public static final List<String> LIFECYCLE_ACTIONS =
            List.of("plugin.activated", "plugin.deactivated", "plugin.uninstalling");

    static final Duration ACTION_TIMEOUT = Duration.ofSeconds(30);
    static final Duration FILTER_TIMEOUT = Duration.ofSeconds(5);
    static final Duration ENDPOINT_TIMEOUT = Duration.ofSeconds(30);
    static final Duration WEBHOOK_TIMEOUT = Duration.ofSeconds(60);

    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistry.class);

    /** An unknown slug. */
    public static final class UnknownPluginException extends RuntimeException {
        UnknownPluginException(String slug) {
            super("Plugin not found: " + slug);
        }
    }

    /** Uninstalling a plugin the package manager installed. */
    public static final class PackageManagedException extends RuntimeException {
        PackageManagedException(String slug) {
            super("Plugin " + slug + " is managed by the package manager");
        }
    }

    /** An SDK endpoint while the runtime cannot serve HTTP. */
    static final class RuntimeUnavailableException extends RuntimeException {
        RuntimeUnavailableException(String message) {
            super(message);
        }
    }

    private final List<PluginEntry> natives;
    private final PluginActivationRepository activations;
    private final PluginStorage storage;
    private final PluginDomain domain;
    private final PluginCapabilities capabilities;
    private final Predicate<Authentication> isAdmin;
    private final TransactionTemplate transactions;
    private final ExecutorService executor;
    private final Set<String> reserved = new HashSet<>();
    private PluginRuntimeBridge bridge;

    PluginRegistry(
            List<EscalatedPlugin> plugins,
            PluginActivationRepository activations,
            PluginStorage storage,
            PluginDomain domain,
            PluginCapabilities capabilities,
            Predicate<Authentication> isAdmin,
            TransactionTemplate transactions,
            ExecutorService executor) {
        this.activations = activations;
        this.storage = storage;
        this.domain = domain;
        this.capabilities = capabilities;
        this.isAdmin = isAdmin;
        this.transactions = transactions;
        this.executor = executor;
        reserved.addAll(EventNames.CANONICAL);
        reserved.addAll(EventNames.LEGACY.keySet());
        reserved.addAll(List.of("ticket.merged", "ticket.split", "ticket.snoozed", "ticket.unsnoozed",
                "ticket.deleted", "reply.updated", "reply.deleted"));
        reserved.addAll(LIFECYCLE_ACTIONS);

        List<PluginEntry> entries = new ArrayList<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (EscalatedPlugin plugin : plugins) {
            PluginEntry entry = PluginEntry.nativePlugin(plugin);
            if (seen.containsKey(entry.slug)) {
                throw new IllegalStateException("Escalated plugins \"" + seen.get(entry.slug) + "\" and \""
                        + entry.name + "\" share the slug \"" + entry.slug + "\"; rename one");
            }
            seen.put(entry.slug, entry.name);
            entries.add(entry);
        }
        this.natives = List.copyOf(entries);
    }

    /** Runs SDK plugins through the runtime (see {@link PluginRuntimeBridge}). */
    void useRuntime(List<String> command, java.io.File directory, Map<String, String> environment, String version) {
        this.bridge = new PluginRuntimeBridge(command, directory, environment, version, this::serveContext, executor);
    }

    @Override
    public void destroy() {
        if (bridge != null) {
            bridge.close();
        }
        executor.shutdownNow();
    }

    PluginStorage storage() {
        return storage;
    }

    PluginDomain domain() {
        return domain;
    }

    boolean reserved(String hook) {
        return reserved.contains(hook);
    }

    /** Whether any plugin can exist here: a native one is registered, or the runtime is on. */
    public boolean hasPlugins() {
        return !natives.isEmpty() || bridge != null;
    }

    // ---- the registry

    /** Native plugins, then the SDK plugins the runtime reports, without slugs a native one has. */
    List<PluginEntry> entries() {
        List<PluginEntry> out = new ArrayList<>(natives);
        if (bridge == null) {
            return out;
        }
        Map<String, String> seen = new LinkedHashMap<>();
        out.forEach(entry -> seen.put(entry.slug, entry.name));
        for (PluginEntry entry : bridge.entries()) {
            if (seen.containsKey(entry.slug)) {
                LOG.warn("Escalated plugins \"{}\" and \"{}\" share the slug \"{}\"; \"{}\" is not loaded",
                        seen.get(entry.slug), entry.name, entry.slug, entry.name);
                continue;
            }
            seen.put(entry.slug, entry.name);
            out.add(entry);
        }
        return out;
    }

    PluginEntry find(String slug) {
        return entries().stream().filter(entry -> entry.slug.equals(slug)).findFirst().orElse(null);
    }

    private Map<String, PluginActivation> activationRows() {
        Map<String, PluginActivation> rows = new LinkedHashMap<>();
        for (PluginActivation row : activations.findAll()) {
            rows.put(row.getSlug(), row);
        }
        return rows;
    }

    private boolean isActive(String slug) {
        return activations.findBySlug(slug).map(PluginActivation::isActive).orElse(false);
    }

    /** The plugin with this slug when it is active, else null. */
    PluginEntry active(String slug) {
        PluginEntry entry = find(slug);
        return entry != null && isActive(slug) ? entry : null;
    }

    List<PluginEntry> activeEntries() {
        List<PluginEntry> all = entries();
        if (all.isEmpty()) {
            return all;
        }
        Map<String, PluginActivation> rows = activationRows();
        return all.stream().filter(entry -> rows.containsKey(entry.slug) && rows.get(entry.slug).isActive()).toList();
    }

    /** Every registered plugin, active or not, as {@code Escalated/Admin/Plugins/Index} lists it. */
    public List<Map<String, Object>> list() {
        List<PluginEntry> all = entries();
        Map<String, PluginActivation> rows = all.isEmpty() ? Map.of() : activationRows();
        List<Map<String, Object>> out = new ArrayList<>();
        for (PluginEntry entry : all) {
            PluginActivation row = rows.get(entry.slug);
            boolean active = row != null && row.isActive();
            PluginManifest m = entry.manifest;
            long menus = m.pages().stream()
                    .filter(page -> page.menu() != null && !PluginEntry.pageRoute(page).isEmpty()).count();
            Map<String, Object> provides = new LinkedHashMap<>();
            provides.put("menu_items", menus);
            provides.put("widgets", m.widgets().size());
            provides.put("page_components", m.components().size());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("slug", entry.slug);
            item.put("name", entry.name);
            item.put("description", blankToNull(m.description()));
            item.put("version", m.version() == null || m.version().isBlank() ? "1.0.0" : m.version());
            item.put("author", blankToNull(m.author()));
            item.put("author_url", blankToNull(m.homepage()));
            item.put("homepage", blankToNull(m.homepage()));
            item.put("is_active", active);
            item.put("activated_at", active && row.getActivatedAt() != null ? row.getActivatedAt().toString() : null);
            item.put("installed_at", row != null && row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
            item.put("source", entry.source);
            item.put("provides", provides);
            out.add(item);
        }
        return out;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    // ---- lifecycle

    /**
     * Switches a plugin on: the row, then {@code onActivate} (the runtime's {@code activate}), then
     * {@code plugin.activated}. Activating an active plugin does nothing.
     */
    public void activate(String slug, Authentication actor) {
        PluginEntry entry = find(slug);
        if (entry == null) {
            throw new UnknownPluginException(slug);
        }
        if (isActive(slug)) {
            return;
        }
        transactions.executeWithoutResult(status -> {
            PluginActivation row = activations.findBySlug(slug).orElseGet(PluginActivation::new);
            row.setSlug(slug);
            row.setActive(true);
            row.setActivatedAt(Instant.now());
            row.setDeactivatedAt(null);
            activations.save(row);
        });
        lifecycle(entry, true, actor);
        dispatchAction("plugin.activated", lifecyclePayload(entry), actor);
    }

    /**
     * Switches a plugin off: {@code plugin.deactivated} while it is still active, {@code onDeactivate}
     * (the runtime's {@code deactivate}), then the row. Deactivating an inactive plugin does nothing.
     */
    public void deactivate(String slug, Authentication actor) {
        PluginEntry entry = find(slug);
        if (entry == null) {
            throw new UnknownPluginException(slug);
        }
        if (!isActive(slug)) {
            return;
        }
        dispatchAction("plugin.deactivated", lifecyclePayload(entry), actor);
        lifecycle(entry, false, actor);
        transactions.executeWithoutResult(status -> activations.findBySlug(slug).ifPresent(row -> {
            row.setActive(false);
            row.setDeactivatedAt(Instant.now());
            activations.save(row);
        }));
    }

    /**
     * Fires {@code plugin.uninstalling}, deactivates the plugin and deletes its row. A registered
     * plugin stays listed, inactive, because its code is in the build; its store rows are kept for a
     * reinstall. A package-managed plugin is refused.
     */
    public void uninstall(String slug, Authentication actor) {
        PluginEntry entry = find(slug);
        if (entry == null) {
            throw new UnknownPluginException(slug);
        }
        if (SOURCE_COMPOSER.equals(entry.source)) {
            throw new PackageManagedException(slug);
        }
        if (isActive(slug)) {
            dispatchAction("plugin.uninstalling", lifecyclePayload(entry), actor);
        }
        deactivate(slug, actor);
        transactions.executeWithoutResult(status -> activations.findBySlug(slug).ifPresent(activations::delete));
    }

    private static Map<String, Object> lifecyclePayload(PluginEntry entry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plugin", entry.name);
        payload.put("slug", entry.slug);
        return payload;
    }

    /** {@code onActivate} or {@code onDeactivate}. A failure is logged with the plugin's name and undoes nothing. */
    private void lifecycle(PluginEntry entry, boolean activate, Authentication actor) {
        String hook = activate ? "onActivate" : "onDeactivate";
        try {
            if (entry.sdk) {
                bridge.call(new PluginRuntimeBridge.Scope(List.of(entry.name), actor), activate ? "activate" : "deactivate",
                        Map.of("plugin", entry.name), ACTION_TIMEOUT);
                return;
            }
            PluginContext ctx = new PluginContext(this, entry, actor);
            runNative(ACTION_TIMEOUT, () -> {
                if (activate) {
                    entry.plugin.onActivate(ctx);
                } else {
                    entry.plugin.onDeactivate(ctx);
                }
                return null;
            });
        } catch (Exception ex) {
            LOG.warn("Escalated plugin {} {} failed: {}", entry.name, hook, describe(ex));
        }
    }

    /** Runs plugin code with a timeout. An overrunning handler is interrupted and abandoned. */
    private <T> T runNative(Duration timeout, Callable<T> work) throws Exception {
        Future<T> future = executor.submit(work);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new TimeoutException("timed out after " + timeout.toSeconds() + " s");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw ex;
        }
    }

    private static String describe(Throwable ex) {
        return ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
    }

    // ---- hooks

    /**
     * Runs {@code hook} on every active plugin that handles it, side by side, and returns when they
     * finish or time out (30 s). A failing or slow handler is logged and never reaches the caller.
     * {@code actor} is the signed-in user the dispatch acts for, or null.
     */
    public void dispatchAction(String hook, Map<String, Object> event, Authentication actor) {
        if (!hasPlugins()) {
            return;
        }
        List<PluginEntry> active;
        Object payload;
        try {
            active = activeEntries();
            payload = PluginJson.plain(event == null ? Map.of() : event);
        } catch (RuntimeException ex) {
            LOG.warn("Escalated plugin action {}: {}", hook, describe(ex));
            return;
        }
        List<Future<?>> running = new ArrayList<>();
        List<String> sdkNames = new ArrayList<>();
        boolean sdkHandles = false;
        for (PluginEntry entry : active) {
            if (entry.sdk) {
                sdkNames.add(entry.name);
                sdkHandles |= entry.actionHooks.contains(hook);
                continue;
            }
            PluginAction action = entry.actions.get(hook);
            if (action == null) {
                continue;
            }
            running.add(executor.submit(() -> {
                Map<String, Object> copy = PluginJson.plainRecord(payload);
                try {
                    runNative(ACTION_TIMEOUT, () -> {
                        action.handle(copy, new PluginContext(this, entry, actor));
                        return null;
                    });
                } catch (Exception ex) {
                    LOG.warn("Escalated plugin {} action {} failed: {}", entry.name, hook, describe(ex));
                }
            }));
        }
        if (sdkHandles) {
            running.add(executor.submit(() -> {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("hook", hook);
                params.put("event", payload);
                params.put("plugins", sdkNames);
                try {
                    bridge.call(new PluginRuntimeBridge.Scope(List.copyOf(sdkNames), actor), "action", params,
                            ACTION_TIMEOUT);
                } catch (Exception ex) {
                    LOG.warn("Escalated plugin action {} (runtime): {}", hook, describe(ex));
                }
            }));
        }
        for (Future<?> future : running) {
            try {
                future.get(ACTION_TIMEOUT.toMillis() + 1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException ex) {
                LOG.warn("Escalated plugin action {}: {}", hook, describe(ex));
            }
        }
    }

    /** {@link #applyFilter(String, Object, Authentication)} acting for no user. */
    public Object applyFilter(String hook, Object value) {
        return applyFilter(hook, value, null);
    }

    /**
     * Passes {@code value} through active plugins' filters for {@code hook}, lowest priority first,
     * and returns the result. A filter that throws or takes longer than 5 s is skipped and the value
     * carries on. The runtime applies its SDK plugins' filters in one step, at the priority of the
     * lowest of them.
     */
    public Object applyFilter(String hook, Object value, Authentication actor) {
        if (!hasPlugins()) {
            return value;
        }
        List<PluginEntry> active;
        try {
            active = activeEntries();
        } catch (RuntimeException ex) {
            LOG.warn("Escalated plugin filter {}: {}", hook, describe(ex));
            return value;
        }
        record Step(int priority, PluginEntry entry, PluginFilter filter) {
        }
        List<Step> steps = new ArrayList<>();
        List<String> sdkNames = new ArrayList<>();
        Integer sdkPriority = null;
        for (PluginEntry entry : active) {
            if (entry.sdk) {
                sdkNames.add(entry.name);
                Integer priority = entry.sdkFilters.get(hook);
                if (priority != null && (sdkPriority == null || priority < sdkPriority)) {
                    sdkPriority = priority;
                }
                continue;
            }
            for (PluginFilter filter : entry.filters) {
                if (filter.hook().equals(hook)) {
                    steps.add(new Step(filter.priority(), entry, filter));
                }
            }
        }
        if (sdkPriority != null) {
            steps.add(new Step(sdkPriority, null, null));
        }
        steps.sort(Comparator.comparingInt(Step::priority));

        Object current = value;
        for (Step step : steps) {
            Object input = current;
            try {
                if (step.entry() == null) {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("hook", hook);
                    params.put("value", input);
                    params.put("plugins", sdkNames);
                    JsonNode result = bridge.call(new PluginRuntimeBridge.Scope(List.copyOf(sdkNames), actor), "filter",
                            params, FILTER_TIMEOUT);
                    current = PluginJson.fromNode(result);
                } else {
                    PluginContext ctx = new PluginContext(this, step.entry(), actor);
                    current = runNative(FILTER_TIMEOUT, () -> step.filter().handler().apply(input, ctx));
                }
            } catch (Exception ex) {
                LOG.warn("Escalated plugin {} filter {} failed: {}",
                        step.entry() == null ? "runtime" : step.entry().name, hook, describe(ex));
                current = input;
            }
        }
        return current;
    }

    // ---- capabilities and UI

    /** Whether {@code user} holds {@code capability}; no capability means anyone the route lets in. */
    public boolean allows(Authentication user, String capability) {
        if (capability == null || capability.isBlank()) {
            return true;
        }
        if (!PluginDomain.signedIn(user)) {
            return false;
        }
        return capabilities.allows(user, capability);
    }

    /**
     * {@code escalated.plugins} for one viewer: active plugins' menu items, page components and
     * dashboard widgets, without the items whose capability the viewer lacks and without admin menu
     * items for anyone but an admin. Empty when no plugin is active. {@code plugin} is the slug.
     */
    public Map<String, Object> uiExtensions(Authentication viewer) {
        if (!hasPlugins()) {
            return new LinkedHashMap<>();
        }
        List<PluginEntry> active;
        try {
            active = activeEntries();
        } catch (RuntimeException ex) {
            LOG.warn("Escalated plugin UI extensions: {}", describe(ex));
            return new LinkedHashMap<>();
        }
        if (active.isEmpty()) {
            return new LinkedHashMap<>();
        }
        boolean admin = PluginDomain.signedIn(viewer) && isAdmin.test(viewer);
        List<Map<String, Object>> menuItems = new ArrayList<>();
        Map<String, List<Map<String, Object>>> pageComponents = new LinkedHashMap<>();
        List<Map<String, Object>> dashboardWidgets = new ArrayList<>();
        for (PluginEntry entry : active) {
            for (PluginManifest.Page page : entry.manifest.pages()) {
                String route = PluginEntry.pageRoute(page);
                if (page.menu() == null || route.isEmpty() || page.component() == null || page.component().isBlank()) {
                    continue;
                }
                String section = page.menu().section() == null || page.menu().section().isBlank()
                        ? "admin" : page.menu().section();
                if ("admin".equals(section) && !admin) {
                    continue;
                }
                if (!allows(viewer, page.capability())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("label", page.menu().label());
                item.put("route", pageUrl(entry.slug, route));
                item.put("icon", blankToNull(page.menu().icon()));
                item.put("position", page.menu().position() == null ? 100 : page.menu().position());
                item.put("section", section);
                item.put("plugin", entry.slug);
                menuItems.add(item);
            }
            for (PluginManifest.SlotComponent component : entry.manifest.components()) {
                if (isBlank(component.page()) || isBlank(component.slot()) || isBlank(component.component())
                        || !allows(viewer, component.capability())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("plugin", entry.slug);
                item.put("component", component.component());
                item.put("data", component.props() == null ? new LinkedHashMap<>() : PluginJson.plain(component.props()));
                item.put("position", component.order() == null ? 100 : component.order());
                pageComponents.computeIfAbsent(component.page() + "." + component.slot(), key -> new ArrayList<>())
                        .add(item);
            }
            for (PluginManifest.Widget widget : entry.manifest.widgets()) {
                if (isBlank(widget.component()) || !allows(viewer, widget.capability())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", entry.slug + "." + widget.component());
                item.put("title", widget.label());
                item.put("component", widget.component());
                item.put("plugin", entry.slug);
                item.put("position", widget.order() == null ? 100 : widget.order());
                item.put("width", isBlank(widget.size()) ? "half" : widget.size());
                if (entry.sdk && entry.badges.contains(widget.component())) {
                    item.put("badge", badge(entry, widget.component(), viewer));
                }
                dashboardWidgets.add(item);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("menuItems", menuItems);
        out.put("pageComponents", pageComponents);
        out.put("dashboardWidgets", dashboardWidgets);
        return out;
    }

    /** A widget's badge from the runtime, asked as the viewer within 5 s; null when that fails. */
    private Object badge(PluginEntry entry, String component, Authentication viewer) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("plugin", entry.name);
        params.put("component", component);
        try {
            JsonNode answer = bridge.call(new PluginRuntimeBridge.Scope(List.of(entry.name), viewer), "badge", params,
                    FILTER_TIMEOUT);
            if (answer != null && answer.isNumber()) {
                return answer.numberValue();
            }
            return answer != null && answer.isTextual() ? answer.asText() : null;
        } catch (Exception ex) {
            LOG.warn("Escalated plugin {} badge {} failed: {}", entry.name, component, describe(ex));
            return null;
        }
    }

    /**
     * Runs, side by side, every cron schedule of every active SDK plugin that is due in the minute of
     * {@code now} (UTC), through the runtime's {@code cron} call, as no user and within 30 s each. A
     * failing or unreadable schedule is logged and never stops the others.
     */
    public void runDueCron(Instant now) {
        if (bridge == null) {
            return;
        }
        List<PluginEntry> active;
        try {
            active = activeEntries();
        } catch (RuntimeException ex) {
            LOG.warn("Escalated plugin cron: {}", describe(ex));
            return;
        }
        List<Future<?>> running = new ArrayList<>();
        for (PluginEntry entry : active) {
            if (!entry.sdk) {
                continue;
            }
            for (String schedule : entry.cron) {
                boolean due;
                try {
                    due = PluginCron.due(schedule, now);
                } catch (IllegalArgumentException ex) {
                    LOG.warn("Escalated plugin {} cron \"{}\" skipped: {}", entry.name, schedule, ex.getMessage());
                    continue;
                }
                if (!due) {
                    continue;
                }
                running.add(executor.submit(() -> {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("plugin", entry.name);
                    params.put("schedule", schedule);
                    try {
                        bridge.call(new PluginRuntimeBridge.Scope(List.of(entry.name), null), "cron", params,
                                ACTION_TIMEOUT);
                    } catch (Exception ex) {
                        LOG.warn("Escalated plugin {} cron \"{}\" failed: {}", entry.name, schedule, describe(ex));
                    }
                }));
            }
        }
        for (Future<?> future : running) {
            try {
                future.get(ACTION_TIMEOUT.toMillis() + 1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException ex) {
                LOG.warn("Escalated plugin cron: {}", describe(ex));
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** A plugin page's URL. */
    public static String pageUrl(String slug, String route) {
        return "/escalated/admin/plugins/" + slug + "/" + route;
    }

    // ---- pages, endpoints and webhooks

    /** A page an active plugin declares; null when the plugin is inactive or has no such page. */
    record ResolvedPage(PluginEntry entry, PluginManifest.Page page) {
    }

    ResolvedPage page(String slug, String route) {
        PluginEntry entry = active(slug);
        PluginManifest.Page page = entry == null ? null : entry.page(route);
        return page == null ? null : new ResolvedPage(entry, page);
    }

    /** The props of a page: the result of the plugin's own {@code GET /{component}}, or {} when it has none or fails. */
    Map<String, Object> pageProps(PluginEntry entry, PluginManifest.Page page, Authentication actor) {
        ParsedRoute.Match match = ParsedRoute.find(entry.endpoints, "GET", "/" + page.component());
        if (match == null) {
            return new LinkedHashMap<>();
        }
        PluginRequest request = new PluginRequest("GET", match.route().path(), null, new byte[0], match.params(),
                Map.of(), Map.of(), null);
        try {
            Object result = invoke(entry, match.route(), request, false, actor);
            if (result instanceof PluginResponse response) {
                result = response.body();
            }
            Map<String, Object> envelope = PluginJson.record(result);
            if (envelope != null && envelope.containsKey("__escalated_http")) {
                result = envelope.get("body");
            }
            Map<String, Object> props = PluginJson.record(PluginJson.plain(result));
            return props == null ? new LinkedHashMap<>() : props;
        } catch (Exception ex) {
            LOG.warn("Escalated plugin {} page props for {} failed: {}", entry.name, page.component(), describe(ex));
            return new LinkedHashMap<>();
        }
    }

    /**
     * Runs one endpoint or webhook: the handler of a native plugin, or the runtime's
     * {@code endpoint}/{@code webhook} call for an SDK plugin.
     */
    Object invoke(PluginEntry entry, ParsedRoute route, PluginRequest request, boolean webhook, Authentication actor)
            throws Exception {
        Duration timeout = webhook ? WEBHOOK_TIMEOUT : ENDPOINT_TIMEOUT;
        if (!entry.sdk) {
            PluginContext ctx = new PluginContext(this, entry, actor);
            return runNative(timeout, () -> route.handler().handle(request, ctx));
        }
        boolean http;
        try {
            http = bridge.supportsHttp();
        } catch (PluginRuntimeBridge.UnavailableException ex) {
            throw new RuntimeUnavailableException(ex.getMessage());
        }
        if (!http) {
            throw new RuntimeUnavailableException("the plugin runtime does not support HTTP contract version 1");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("plugin", entry.name);
        params.put("method", route.method());
        params.put("path", route.path());
        params.put("body", request.body());
        params.put("params", request.params());
        params.put("query", request.query());
        params.put("headers", request.headers());
        params.put("httpContract", 1);
        params.put("rawBodyBase64", Base64.getEncoder().encodeToString(request.rawBody()));
        if (request.clientIp() != null) {
            params.put("clientIp", request.clientIp());
        }
        try {
            JsonNode result = bridge.call(new PluginRuntimeBridge.Scope(List.of(entry.name), actor),
                    webhook ? "webhook" : "endpoint", params, timeout);
            return PluginJson.fromNode(result);
        } catch (PluginRuntimeBridge.UnavailableException ex) {
            throw new RuntimeUnavailableException(ex.getMessage());
        }
    }

    /**
     * The ctx of the plugin with this slug, active or not, for host code that seeds or reads a
     * plugin's config and store. It acts for no user.
     */
    public PluginContext contextFor(String slug) {
        PluginEntry entry = find(slug);
        if (entry == null) {
            throw new UnknownPluginException(slug);
        }
        return new PluginContext(this, entry, null);
    }

    // ---- the runtime's ctx.* calls

    @SuppressWarnings("unchecked")
    private Object serveContext(String plugin, String method, Map<String, Object> p, Authentication actor) {
        PluginEntry entry = bridge == null ? null : bridge.known().stream()
                .filter(candidate -> candidate.name.equals(plugin)).findFirst().orElse(null);
        if (entry == null) {
            throw new IllegalArgumentException("unknown plugin \"" + plugin + "\"");
        }
        PluginContext ctx = new PluginContext(this, entry, actor);
        Map<String, Object> data = PluginJson.record(p.get("data"));
        Map<String, Object> filter = PluginJson.record(p.get("filter"));
        switch (method) {
            case "ctx.config.all":
                return ctx.config().all();
            case "ctx.config.get":
                return ctx.config().get(PluginJson.string(p, "key"));
            case "ctx.config.set":
                if (data == null) {
                    throw new IllegalArgumentException("ctx.config.set requires an object");
                }
                ctx.config().set(data);
                return null;
            case "ctx.store.get":
                return ctx.store().get(PluginJson.string(p, "collection"), PluginJson.string(p, "key"));
            case "ctx.store.set":
                ctx.store().set(PluginJson.string(p, "collection"), PluginJson.string(p, "key"), p.get("value"));
                return null;
            case "ctx.store.query":
                return ctx.store().query(PluginJson.string(p, "collection"), filter, PluginJson.record(p.get("options")));
            case "ctx.store.insert":
                return ctx.store().insert(PluginJson.string(p, "collection"), data);
            case "ctx.store.update":
                return ctx.store().update(PluginJson.string(p, "collection"), PluginJson.string(p, "key"), data);
            case "ctx.store.delete":
                ctx.store().delete(PluginJson.string(p, "collection"), PluginJson.string(p, "key"));
                return null;
            case "ctx.log":
                String message = PluginJson.string(p, "message");
                switch (String.valueOf(p.get("level"))) {
                    case "warn" -> ctx.log().warn(message, p.get("data"));
                    case "error" -> ctx.log().error(message, p.get("data"));
                    case "debug" -> ctx.log().debug(message, p.get("data"));
                    default -> ctx.log().info(message, p.get("data"));
                }
                return null;
            case "ctx.emit":
                ctx.emit(PluginJson.string(p, "hook"), p.get("data"));
                return null;
            case "ctx.currentUser":
                return ctx.currentUser();
            case "ctx.tickets.find":
                return ctx.tickets().find(p.get("id"));
            case "ctx.tickets.query":
                return ctx.tickets().query(filter == null ? Map.of() : filter);
            case "ctx.tickets.create":
                return ctx.tickets().create(data);
            case "ctx.tickets.update":
                return ctx.tickets().update(p.get("id"), data);
            case "ctx.replies.find":
                return ctx.replies().find(p.get("id"));
            case "ctx.replies.query":
                return ctx.replies().query(filter == null ? Map.of() : filter);
            case "ctx.replies.create":
                return ctx.replies().create(data);
            case "ctx.contacts.find":
                return ctx.contacts().find(p.get("id"));
            case "ctx.contacts.findByEmail":
                return ctx.contacts().findByEmail(PluginJson.string(p, "email"));
            case "ctx.contacts.create":
                return ctx.contacts().create(data == null ? Map.of() : data);
            case "ctx.tags.all":
                return ctx.tags().all();
            case "ctx.tags.create":
                return ctx.tags().create(data == null ? Map.of() : data);
            case "ctx.departments.all":
                return ctx.departments().all();
            case "ctx.departments.find":
                return ctx.departments().find(p.get("id"));
            case "ctx.agents.all":
                return ctx.agents().all();
            case "ctx.agents.find":
                return ctx.agents().find(p.get("id"));
            case "ctx.broadcast.toChannel":
                ctx.broadcast().toChannel(PluginJson.string(p, "channel"), PluginJson.string(p, "event"), p.get("data"));
                return null;
            case "ctx.broadcast.toUser":
                ctx.broadcast().toUser(p.get("userId"), PluginJson.string(p, "event"), p.get("data"));
                return null;
            case "ctx.broadcast.toTicket":
                ctx.broadcast().toTicket(p.get("ticketId"), PluginJson.string(p, "event"), p.get("data"));
                return null;
            default:
                throw new IllegalArgumentException("unknown method " + method);
        }
    }
}
