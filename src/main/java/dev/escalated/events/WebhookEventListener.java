package dev.escalated.events;

import dev.escalated.services.WebhookService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers webhooks for ticket and reply events.
 *
 * <p>After the change that raised the event has committed, and on another
 * thread. A receiver that fetches the ticket it was just told about must find
 * it; a slow or failing endpoint must neither hold the request open nor roll the
 * ticket back. {@code fallbackExecution} keeps events published outside a
 * transaction from being dropped.
 */
@Component
public class WebhookEventListener {

    private final WebhookService webhookService;

    public WebhookEventListener(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onTicketEvent(TicketEvent event) {
        Long ticketId = event.getTicket().getId();
        for (String eventName : EventNames.forTicketEvent(event.getType())) {
            Map<String, Object> data = detail(event);
            if (data.isEmpty()) {
                webhookService.dispatchEvent(eventName, ticketId);
            } else {
                webhookService.dispatchEvent(eventName, ticketId, data);
            }
        }
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onReplyEvent(ReplyEvent event) {
        webhookService.dispatchEvent(EventNames.forReply(event), event.getReply().getId());
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onChatEvent(ChatEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chat_session_id", event.getSessionId());
        data.put("ticket_id", event.getTicketId());
        if (event.getAgentId() != null) {
            data.put("agent_id", event.getAgentId());
        }
        webhookService.dispatchEvent(event.getType().eventName(), event.getTicketId(), data);
    }

    /** Extra payload keys for events that carry more than the ticket id. */
    private static Map<String, Object> detail(TicketEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        switch (event.getType()) {
            case SLA_WARNING, SLA_BREACHED -> data.put("sla_type", event.getNewValue());
            case ESCALATED -> data.put("reason", event.getNewValue());
            case TAG_ADDED, TAG_REMOVED -> data.put("tag", event.getNewValue());
            case DEPARTMENT_CHANGED -> {
                data.put("old_department_id", event.getOldValue());
                data.put("department_id", event.getNewValue());
            }
            default -> {
            }
        }
        if (event.hasStatusDetail()) {
            // Added for custom statuses; the payload is an open map, so nothing existing changes.
            data.put("old_custom_status", event.getOldCustomStatus());
            data.put("new_custom_status", event.getNewCustomStatus());
            data.put("old_category", event.getOldCategory());
            data.put("new_category", event.getNewCategory());
        }
        return data;
    }
}
