package dev.escalated.plugins;

/**
 * An endpoint or webhook. {@code method} is GET, POST, PUT, PATCH, DELETE, OPTIONS or HEAD;
 * {@code path} starts with a slash and may hold {@code :name} segments, which arrive as
 * {@link PluginRequest#params()}. {@code capability}, on an endpoint, is one the user must hold
 * (403 without it); null for none.
 */
public record PluginRoute(String method, String path, String capability, Handler handler) {

    /**
     * Answers the request. Return a value for a JSON 200, or a {@link PluginResponse} to choose the
     * status, headers and format. A throw is a 500 whose body says nothing about it.
     */
    @FunctionalInterface
    public interface Handler {
        Object handle(PluginRequest request, PluginContext ctx) throws Exception;
    }

    public static PluginRoute of(String method, String path, Handler handler) {
        return new PluginRoute(method, path, null, handler);
    }
}
