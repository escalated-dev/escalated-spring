package dev.escalated.plugins;

/**
 * A native plugin widget's live badge: the same thing as an SDK widget's {@code badge} function.
 * Asked as the viewer while {@code escalated.plugins.dashboardWidgets} is built, within 5 s. The
 * answer is a number, a string or null; a throw or a timeout is null.
 */
@FunctionalInterface
public interface PluginBadge {

    Object count(PluginContext ctx) throws Exception;
}
