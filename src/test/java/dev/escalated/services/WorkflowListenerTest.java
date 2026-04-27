package dev.escalated.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.escalated.events.ReplyEvent;
import dev.escalated.events.TicketEvent;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link WorkflowListener}. Confirms event → trigger
 * mapping and that unhandled event types are silently ignored.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowListenerTest {

    @Mock private WorkflowRunnerService runner;

    private WorkflowListener listener;

    @BeforeEach
    void setUp() {
        listener = new WorkflowListener(runner);
    }

    private Ticket newTicket() {
        Ticket t = new Ticket();
        t.setId(1L);
        return t;
    }

    @Test
    void onTicketEvent_created_routesToTicketCreated() {
        Ticket t = newTicket();
        listener.onTicketEvent(new TicketEvent(this, t, TicketEvent.Type.CREATED, null));

        verify(runner).runForEvent(eq("ticket.created"), eq(t));
    }

    @Test
    void onTicketEvent_updated_routesToTicketUpdated() {
        Ticket t = newTicket();
        listener.onTicketEvent(new TicketEvent(this, t, TicketEvent.Type.UPDATED, null));

        verify(runner).runForEvent(eq("ticket.updated"), eq(t));
    }

    @Test
    void onTicketEvent_assigned_routesToTicketAssigned() {
        Ticket t = newTicket();
        listener.onTicketEvent(new TicketEvent(this, t, TicketEvent.Type.ASSIGNED, null));

        verify(runner).runForEvent(eq("ticket.assigned"), eq(t));
    }

    @Test
    void onTicketEvent_statusChanged_routesToStatusChanged() {
        Ticket t = newTicket();
        listener.onTicketEvent(new TicketEvent(this, t, TicketEvent.Type.STATUS_CHANGED, null));

        verify(runner).runForEvent(eq("ticket.status_changed"), eq(t));
    }

    @Test
    void onTicketEvent_resolved_mappedToStatusChanged() {
        Ticket t = newTicket();
        listener.onTicketEvent(new TicketEvent(this, t, TicketEvent.Type.RESOLVED, null));

        verify(runner).runForEvent(eq("ticket.status_changed"), eq(t));
    }

    @Test
    void onTicketEvent_priorityChanged_routesToPriorityChanged() {
        Ticket t = newTicket();
        listener.onTicketEvent(new TicketEvent(this, t, TicketEvent.Type.PRIORITY_CHANGED, null));

        verify(runner).runForEvent(eq("ticket.priority_changed"), eq(t));
    }

    @Test
    void onTicketEvent_reopened_routesToReopened() {
        Ticket t = newTicket();
        listener.onTicketEvent(new TicketEvent(this, t, TicketEvent.Type.REOPENED, null));

        verify(runner).runForEvent(eq("ticket.reopened"), eq(t));
    }

    @Test
    void onTicketEvent_merged_isIgnored() {
        Ticket t = newTicket();
        listener.onTicketEvent(new TicketEvent(this, t, TicketEvent.Type.MERGED, null));

        verify(runner, never()).runForEvent(any(), any());
    }

    @Test
    void onTicketEvent_deleted_isIgnored() {
        Ticket t = newTicket();
        listener.onTicketEvent(new TicketEvent(this, t, TicketEvent.Type.DELETED, null));

        verify(runner, never()).runForEvent(any(), any());
    }

    @Test
    void onReplyEvent_created_routesToReplyCreated() {
        Ticket t = newTicket();
        Reply r = new Reply();
        r.setTicket(t);
        listener.onReplyEvent(new ReplyEvent(this, r, ReplyEvent.Type.CREATED, null));

        verify(runner).runForEvent(eq("reply.created"), eq(t));
    }

    @Test
    void onReplyEvent_updated_isIgnored() {
        Ticket t = newTicket();
        Reply r = new Reply();
        r.setTicket(t);
        listener.onReplyEvent(new ReplyEvent(this, r, ReplyEvent.Type.UPDATED, null));

        verify(runner, never()).runForEvent(any(), any());
    }

    @Test
    void onReplyEvent_missingTicket_isIgnored() {
        Reply r = new Reply();
        listener.onReplyEvent(new ReplyEvent(this, r, ReplyEvent.Type.CREATED, null));

        verify(runner, never()).runForEvent(any(), any());
    }
}
