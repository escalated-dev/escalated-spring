package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

/**
 * A host user following a ticket — a notification target alongside the assignee
 * and requester. Recorded via the add_follower workflow action. Unique per
 * (ticket_id, user_id). See issue #74.
 */
@Entity
@Table(
        name = "escalated_ticket_followers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ticket_id", "user_id"}))
public class TicketFollower extends BaseEntity {

    @NotNull
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private String userId;

    protected TicketFollower() {}

    public TicketFollower(Long ticketId, String userId) {
        this.ticketId = ticketId;
        this.userId = userId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getUserId() {
        return userId;
    }
}
