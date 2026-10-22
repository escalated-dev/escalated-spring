package dev.escalated.events;

import dev.escalated.models.Ticket;
import org.springframework.context.ApplicationEvent;

public class TicketEvent extends ApplicationEvent {

    public enum Type {
        CREATED, UPDATED, ASSIGNED, STATUS_CHANGED, PRIORITY_CHANGED,
        CLOSED, RESOLVED, REOPENED, MERGED, SPLIT, SNOOZED, UNSNOOZED, DELETED,
        UNASSIGNED, DEPARTMENT_CHANGED, TAG_ADDED, TAG_REMOVED, ESCALATED, SLA_WARNING, SLA_BREACHED
    }

    private final Ticket ticket;
    private final Type type;
    private final String actorEmail;
    private final String oldValue;
    private final String newValue;
    private boolean statusDetail;
    private String oldCustomStatus;
    private String newCustomStatus;
    private String oldCategory;
    private String newCategory;

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

    /**
     * For a status change: the custom status slugs (null when none) and the categories either side.
     * The built-in old and new status stay in {@link #getOldValue()} and {@link #getNewValue()}.
     */
    public TicketEvent withCustomStatuses(String oldCustom, String newCustom, String oldCat, String newCat) {
        this.statusDetail = true;
        this.oldCustomStatus = oldCustom;
        this.newCustomStatus = newCustom;
        this.oldCategory = oldCat;
        this.newCategory = newCat;
        return this;
    }

    public boolean hasStatusDetail() {
        return statusDetail;
    }

    public String getOldCustomStatus() {
        return oldCustomStatus;
    }

    public String getNewCustomStatus() {
        return newCustomStatus;
    }

    public String getOldCategory() {
        return oldCategory;
    }

    public String getNewCategory() {
        return newCategory;
    }
}
