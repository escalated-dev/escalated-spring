package dev.escalated.events;

import dev.escalated.models.Ticket;
import org.springframework.context.ApplicationEvent;

public class TicketEvent extends ApplicationEvent {

    public enum Type {
        CREATED, UPDATED, ASSIGNED, STATUS_CHANGED, PRIORITY_CHANGED,
        CLOSED, RESOLVED, REOPENED, MERGED, SPLIT, SNOOZED, UNSNOOZED, DELETED
    }

    private final Ticket ticket;
    private final Type type;
    private final String actorEmail;
    private final String oldValue;
    private final String newValue;

    public TicketEvent(Object source, Ticket ticket, Type type, String actorEmail) {
        this(source, ticket, type, actorEmail, null, null);
    }

    public TicketEvent(Object source, Ticket ticket, Type type, String actorEmail, String oldValue, String newValue) {
        super(source);
        this.ticket = ticket;
        this.type = type;
        this.actorEmail = actorEmail;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public Type getType() {
        return type;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }
}
