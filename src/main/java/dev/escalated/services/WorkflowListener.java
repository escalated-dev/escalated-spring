package dev.escalated.services;

import dev.escalated.events.ReplyEvent;
import dev.escalated.events.TicketEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Bridges Spring {@code ApplicationEvent}s into {@link WorkflowRunnerService}.
 *
 * <p>Listens for {@link TicketEvent} and {@link ReplyEvent} and maps each
 * to the canonical workflow trigger name (matching the 12-event set in
 * {@link WorkflowEngine#TRIGGER_EVENTS}). Runs asynchronously so a slow
 * workflow never blocks the mutation that fired it.
 *
 * <p>Mirrors the NestJS reference {@code workflow.listener.ts} and the
 * Laravel {@code ProcessWorkflows} listener.
 */
@Component
public class WorkflowListener {

    private final WorkflowRunnerService runner;

    public WorkflowListener(WorkflowRunnerService runner) {
        this.runner = runner;
    }

    @EventListener
    @Async
    public void onTicketEvent(TicketEvent event) {
        String trigger = mapTicketTrigger(event.getType());
        if (trigger == null) {
            return;
        }
        runner.runForEvent(trigger, event.getTicket());
    }

    @EventListener
    @Async
    public void onReplyEvent(ReplyEvent event) {
        if (event.getType() != ReplyEvent.Type.CREATED) {
            return;
        }
        if (event.getReply() == null || event.getReply().getTicket() == null) {
            return;
        }
        runner.runForEvent("reply.created", event.getReply().getTicket());
    }

    static String mapTicketTrigger(TicketEvent.Type type) {
        return switch (type) {
            case CREATED -> "ticket.created";
            case UPDATED -> "ticket.updated";
            case ASSIGNED -> "ticket.assigned";
            case STATUS_CHANGED, CLOSED, RESOLVED -> "ticket.status_changed";
            case PRIORITY_CHANGED -> "ticket.priority_changed";
            case REOPENED -> "ticket.reopened";
            // MERGED, SPLIT, SNOOZED, UNSNOOZED, DELETED are not
            // surfaced as workflow triggers today — see
            // WorkflowEngine.TRIGGER_EVENTS for the canonical list.
            default -> null;
        };
    }
}
