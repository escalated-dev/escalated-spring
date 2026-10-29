package dev.escalated.plugins;

import java.util.List;
import java.util.Map;

/**
 * A native plugin: Java code in the host, registered by declaring it as a Spring bean (a
 * {@code @Component} the host's component scan finds, or a {@code @Bean}). Every bean of this type
 * is listed on Admin &gt; Plugins as {@code registered}, inactive until an admin activates it.
 *
 * <p>Only {@link #manifest()} is required. The rest add behaviour and default to nothing. A plugin
 * runs with the host's privileges: register only code you would merge into the host itself.
 *
 * <p>The behaviour every Escalated backend shares is the plugin contract in
 * escalated-developer-context ({@code domain-model/plugin-contract.md}); {@code docs/plugins.md}
 * covers this package.
 */
public interface EscalatedPlugin {

    /** The plugin's identity and the screens it adds. */
    PluginManifest manifest();

    /** Called after an admin activates the plugin. A throw is logged and undoes nothing. */
    default void onActivate(PluginContext ctx) throws Exception {
    }

    /** Called while an admin deactivates the plugin, before its row is switched off. */
    default void onDeactivate(PluginContext ctx) throws Exception {
    }

    /**
     * Actions by hook: the canonical domain events ({@code ticket.created}, {@code reply.created},
     * ...), the lifecycle actions ({@code plugin.activated}, {@code plugin.deactivated},
     * {@code plugin.uninstalling}) and whatever active plugins emit.
     */
    default Map<String, PluginAction> actions() {
        return Map.of();
    }

    /** Filters, run by {@link PluginRegistry#applyFilter}. */
    default List<PluginFilter> filters() {
        return List.of();
    }

    /** Endpoints, served at {@code /escalated/api/plugins/{slug}{path}} behind the admin guard. */
    default List<PluginRoute> endpoints() {
        return List.of();
    }

    /** Webhooks, served at {@code /escalated/webhooks/plugins/{slug}{path}} with no guard. */
    default List<PluginRoute> webhooks() {
        return List.of();
    }

    /**
     * Cron schedules: five-field expressions ({@code minute hour day-of-month month day-of-week},
     * UTC, or {@code @hourly}-style shorthands) to the task to run. While the plugin is active,
     * {@link dev.escalated.scheduling.PluginCronScheduler} runs each schedule in the minutes it is
     * due, as no user, within 30 s. An expression that cannot be read is logged and skipped.
     */
    default Map<String, PluginCronTask> cron() {
        return Map.of();
    }

    /**
     * Badges for this plugin's widgets, by the widget's {@code component}. Each is asked as the
     * viewer while {@code escalated.plugins.dashboardWidgets} is built, and its answer is that
     * widget's {@code badge} ({@code null} when it throws or takes longer than 5 s). A widget with no
     * badge here carries no {@code badge} key.
     */
    default Map<String, PluginBadge> badges() {
        return Map.of();
    }
}
