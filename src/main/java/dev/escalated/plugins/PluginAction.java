package dev.escalated.plugins;

import java.util.Map;

/**
 * Handles one action. The event is the JSON-shaped payload an SDK plugin receives. A throw is
 * logged with the plugin's name and never reaches whatever caused the event.
 */
@FunctionalInterface
public interface PluginAction {

    void handle(Map<String, Object> event, PluginContext ctx) throws Exception;
}
