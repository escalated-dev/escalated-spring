package dev.escalated.services;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedProperties.TicketActionProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Resolves host-defined custom ticket actions registered under the
 * {@code escalated.ticket-actions} configuration list. Each visible action
 * renders as a button on the agent ticket screen; triggering it records an
 * internal note and publishes a {@code CustomActionTriggeredEvent}.
 *
 * <p>Mirrors the Laravel TicketActionRegistry / NestJS reference.
 */
@Service
public class TicketActionRegistry {

    private final List<TicketActionProperties> actions;

    public TicketActionRegistry(EscalatedProperties properties) {
        this.actions = new ArrayList<>();
        for (TicketActionProperties action : properties.getTicketActions()) {
            boolean hasKey = action.getKey() != null && !action.getKey().isEmpty();
            boolean hasLabel = action.getLabel() != null && !action.getLabel().isEmpty();
            if (hasKey && hasLabel) {
                this.actions.add(action);
            }
        }
    }

    /** Find a configured action by key, or {@code null}. */
    public TicketActionProperties find(String key) {
        for (TicketActionProperties action : actions) {
            if (action.getKey().equals(key)) {
                return action;
            }
        }
        return null;
    }

    /**
     * The visible actions, serialized for the UI. The controller adds the
     * {@code url} and {@code method} before responding.
     */
    public List<Map<String, Object>> forTicket() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TicketActionProperties action : actions) {
            if (!action.isVisible()) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key", action.getKey());
            map.put("label", action.getLabel());
            map.put("variant", action.getVariant());
            map.put("confirmation", action.getConfirmation());
            map.put("disabled", !action.isEnabled());
            map.put("metadata", action.getMetadata() != null ? action.getMetadata() : new LinkedHashMap<>());
            result.add(map);
        }
        return result;
    }
}
