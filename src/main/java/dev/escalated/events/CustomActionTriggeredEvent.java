package dev.escalated.events;

import dev.escalated.models.Ticket;
import java.util.Map;
import org.springframework.context.ApplicationEvent;

/**
 * Published when an agent triggers a host-defined custom ticket action.
 * Host applications listen for this with {@code @EventListener} to run their
 * own work (CRM sync, etc.).
 */
public class CustomActionTriggeredEvent extends ApplicationEvent {

    private final Ticket ticket;
    private final String action;
    private final String userEmail;
    private final Map<String, Object> payload;
    private final Map<String, Object> metadata;

    public CustomActionTriggeredEvent(Object source, Ticket ticket, String action, String userEmail,
                                      Map<String, Object> payload, Map<String, Object> metadata) {
        super(source);
        this.ticket = ticket;
        this.action = action;
        this.userEmail = userEmail;
        this.payload = payload;
        this.metadata = metadata;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getAction() {
        return action;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
