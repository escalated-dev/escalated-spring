package dev.escalated.services;

import dev.escalated.events.ReplyEvent;
import dev.escalated.events.TicketEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges Spring {@code ApplicationEvent}s into {@link WorkflowRunnerService}.
 *
 * <p>Listens for {@link TicketEvent} and {@link ReplyEvent} and maps each
 * to the canonical workflow trigger name (matching the 12-event set in
 * {@link WorkflowEngine#TRIGGER_EVENTS}).
 *
 * <p>Runs after the change that raised the event has committed, on another
 * thread, so a slow or failing workflow never blocks the mutation that fired
 * it or rolls it back. The ticket is handed over by id and loaded again there:
 * the instance the event carries belongs to a persistence context that has
 * already closed, and actions such as {@code add_tag} walk its lazy
 * associations. {@code fallbackExecution} keeps events published outside a
 * transaction from being dropped.
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

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onTicketEvent(TicketEvent event) {
        String trigger = mapTicketTrigger(event.getType());
        if (trigger == null || event.getTicket() == null || event.getTicket().getId() == null) {
            return;
        }
        runner.runForEvent(trigger, event.getTicket().getId());
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onReplyEvent(ReplyEvent event) {
        if (event.getType() != ReplyEvent.Type.CREATED) {
            return;
        }
        if (event.getReply() == null || event.getReply().getTicket() == null
                || event.getReply().getTicket().getId() == null) {
            return;
        }
        runner.runForEvent("reply.created", event.getReply().getTicket().getId());
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
