package dev.escalated.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An escalation rule's actions, as escalated-laravel's: a list of {@code {type, value}} run in
 * order. Types: {@code escalate}, {@code change_priority} (a priority name), {@code assign_to} (an
 * agent id) and {@code change_department} (a department id); {@code notify} (logged, as before) is
 * kept for rules converted from this port's older shape.
 */
public final class EscalationActions {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static final List<String> TYPES = List.of("escalate", "change_priority", "assign_to", "change_department");

    private EscalationActions() {}

    /** The stored actions; unreadable JSON is none. */
    public static List<Map<String, Object>> read(String json) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return actions;
        }
        try {
            Object parsed = JSON.readValue(json, Object.class);
            if (parsed instanceof List<?> items) {
                for (Object item : items) {
                    if (item instanceof Map<?, ?> map) {
                        actions.add(JSON.convertValue(map, new TypeReference<Map<String, Object>>() {}));
                    }
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            // No actions.
        }
        return actions;
    }
}
