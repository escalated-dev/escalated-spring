package dev.escalated.events;

import dev.escalated.services.WebhookService;
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
        String eventName = "ticket." + event.getType().name().toLowerCase();
        webhookService.dispatchEvent(eventName, event.getTicket().getId());
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onReplyEvent(ReplyEvent event) {
        String eventName = "reply." + event.getType().name().toLowerCase();
        webhookService.dispatchEvent(eventName, event.getReply().getId());
    }
}
