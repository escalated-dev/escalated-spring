package dev.escalated.plugins;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One registered plugin, normalised once: a native plugin, or an SDK plugin the runtime reports. */
final class PluginEntry {

    final String name;
    final String slug;
    final String source;
    final PluginManifest manifest;
    final EscalatedPlugin plugin;
    final boolean sdk;

    final List<ParsedRoute> endpoints = new ArrayList<>();
    final List<ParsedRoute> webhooks = new ArrayList<>();
    final List<PluginFilter> filters = new ArrayList<>();
    final Map<String, PluginAction> actions = new LinkedHashMap<>();

    /** SDK plugins: the hooks the runtime's manifest declares. */
    final Set<String> actionHooks = new LinkedHashSet<>();
    final Map<String, Integer> sdkFilters = new LinkedHashMap<>();
    /** SDK plugins: the cron schedules, and the widget components that have a badge function. */
    final List<String> cron = new ArrayList<>();
    final Set<String> badges = new LinkedHashSet<>();
    /** Native plugins: the cron tasks by schedule, and the badge functions by widget component. */
    final Map<String, PluginCronTask> cronTasks = new LinkedHashMap<>();
    final Map<String, PluginBadge> nativeBadges = new LinkedHashMap<>();

    private PluginEntry(String name, String source, PluginManifest manifest, EscalatedPlugin plugin, boolean sdk) {
        this.name = name;
        this.slug = PluginSlugs.slug(name);
        this.source = source;
        this.manifest = manifest;
        this.plugin = plugin;
        this.sdk = sdk;
    }

    /** A native plugin; a malformed one is an IllegalStateException naming it. */
    static PluginEntry nativePlugin(EscalatedPlugin plugin) {
        PluginManifest manifest = plugin.manifest();
        if (manifest == null || manifest.name() == null || manifest.name().isBlank()) {
            throw new IllegalStateException("Escalated plugin " + plugin.getClass().getName() + " has no manifest name");
        }
        PluginEntry entry = new PluginEntry(manifest.name(), PluginRegistry.SOURCE_REGISTERED, manifest, plugin, false);
        try {
            for (PluginRoute route : plugin.endpoints()) {
                entry.endpoints.add(checked(entry, route, route.capability()));
            }
            for (PluginRoute route : plugin.webhooks()) {
                entry.webhooks.add(checked(entry, route, null));
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
        for (PluginFilter filter : plugin.filters()) {
            if (filter.handler() == null || filter.hook() == null) {
                throw new IllegalStateException("plugin " + entry.name + ": a filter has no hook or handler");
            }
            entry.filters.add(filter);
        }
        plugin.actions().forEach((hook, action) -> {
            if (action != null) {
                entry.actions.put(hook, action);
            }
        });
        plugin.cron().forEach((schedule, task) -> {
            if (schedule != null && task != null) {
                entry.cron.add(schedule);
                entry.cronTasks.put(schedule, task);
            }
        });
        plugin.badges().forEach((component, badge) -> {
            if (component != null && badge != null) {
                entry.badges.add(component);
                entry.nativeBadges.put(component, badge);
            }
        });
        return entry;
    }

    private static ParsedRoute checked(PluginEntry entry, PluginRoute route, String capability) {
        if (route.handler() == null) {
            throw new IllegalArgumentException(
                    "plugin " + entry.name + ": route \"" + route.method() + " " + route.path() + "\" has no handler");
        }
        return ParsedRoute.parse(entry.name, route.method(), route.path(), capability, route.handler());
    }

    /** An SDK plugin, as the runtime's {@code manifest} describes it. */
    static PluginEntry sdkPlugin(String name, PluginManifest manifest) {
        return new PluginEntry(name, PluginRegistry.SOURCE_COMPOSER, manifest, null, true);
    }

    /** The page with this route, when it is declared with a component; null otherwise. */
    PluginManifest.Page page(String route) {
        for (PluginManifest.Page page : manifest.pages()) {
            String declared = pageRoute(page);
            if (!declared.isEmpty() && page.component() != null && !page.component().isBlank()
                    && declared.equals(route)) {
                return page;
            }
        }
        return null;
    }

    /** A page's route with leading slashes removed; empty means none. */
    static String pageRoute(PluginManifest.Page page) {
        String route = page.route() == null ? "" : page.route();
        int start = 0;
        while (start < route.length() && route.charAt(start) == '/') {
            start++;
        }
        return route.substring(start);
    }
}
