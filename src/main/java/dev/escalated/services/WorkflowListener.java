package dev.escalated.services;

import dev.escalated.events.ChatEvent;
import dev.escalated.events.EventNames;
import dev.escalated.events.ReplyEvent;
import dev.escalated.events.TicketEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges Spring {@code ApplicationEvent}s into {@link WorkflowRunnerService}.
 *
 * <p>Listens for {@link TicketEvent}, {@link ReplyEvent} and {@link ChatEvent}
 * and runs the workflows for each canonical name the event is announced under
 * ({@link EventNames}, the 2026-10-21 event vocabulary).
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
        if (event.getTicket() == null || event.getTicket().getId() == null) {
            return;
        }
        for (String trigger : EventNames.workflowTriggersFor(event.getType())) {
            runner.runForEvent(trigger, event.getTicket().getId());
        }
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
        // An internal note is note.created, never reply.created.
        runner.runForEvent(EventNames.forReply(event), event.getReply().getTicket().getId());
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onChatEvent(ChatEvent event) {
        if (event.getTicketId() != null) {
            runner.runForEvent(event.getType().eventName(), event.getTicketId());
        }
    }
}
