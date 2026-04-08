package dev.escalated.events;

import dev.escalated.services.WebhookService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class WebhookEventListener {

    private final WebhookService webhookService;

    public WebhookEventListener(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Async
    @EventListener
    public void onTicketEvent(TicketEvent event) {
        String eventName = "ticket." + event.getType().name().toLowerCase();
        webhookService.dispatchEvent(eventName, event.getTicket().getId());
    }

    @Async
    @EventListener
    public void onReplyEvent(ReplyEvent event) {
        String eventName = "reply." + event.getType().name().toLowerCase();
        webhookService.dispatchEvent(eventName, event.getReply().getId());
    }
}
