package dev.escalated.plugins;

import java.util.Map;

/**
 * An endpoint's chosen status, headers and format: JSON, or plain text when {@code text} is set
 * (the body must then be a string). Transport headers and {@code Set-Cookie} are refused (502).
 */
public record PluginResponse(int status, Map<String, String> headers, Object body, boolean text) {

    public static PluginResponse json(int status, Object body) {
        return new PluginResponse(status, Map.of(), body, false);
    }

    public static PluginResponse text(int status, String body) {
        return new PluginResponse(status, Map.of(), body, true);
    }
}
