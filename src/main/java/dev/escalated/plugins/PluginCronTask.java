package dev.escalated.plugins;

/**
 * One cron schedule of a native plugin: the same thing as a {@code cron} entry of an SDK plugin.
 * It runs as no user ({@code ctx.currentUser()} is null) within 30 s; a throw is logged and never
 * stops the other schedules.
 */
@FunctionalInterface
public interface PluginCronTask {

    void run(PluginContext ctx) throws Exception;
}
