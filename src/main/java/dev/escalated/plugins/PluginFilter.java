package dev.escalated.plugins;

/**
 * One filter. Filters run in ascending {@code priority} (10 by default); one that throws or takes
 * longer than five seconds is skipped and the value carries on to the next.
 */
public record PluginFilter(String hook, int priority, Handler handler) {

    /** Returns the filtered value. */
    @FunctionalInterface
    public interface Handler {
        Object apply(Object value, PluginContext ctx) throws Exception;
    }

    /** A filter at the default priority, 10. */
    public static PluginFilter of(String hook, Handler handler) {
        return new PluginFilter(hook, 10, handler);
    }
}
