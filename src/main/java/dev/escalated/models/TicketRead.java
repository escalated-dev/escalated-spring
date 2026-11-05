package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * When an agent last read a ticket (ADR 2026-11-05-agent-panel-data). A null {@code lastReadAt}
 * is a ticket the agent marked unread. Unique per agent and ticket.
 */
@Entity
@Table(
        name = "escalated_ticket_reads",
        uniqueConstraints = @UniqueConstraint(name = "uq_escalated_ticket_reads_agent_ticket",
                columnNames = {"agent_id", "ticket_id"}))
public class TicketRead extends BaseEntity {

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    protected TicketRead() {}

    public TicketRead(Long ticketId, Long agentId, Instant lastReadAt) {
        this.ticketId = ticketId;
        this.agentId = agentId;
        this.lastReadAt = lastReadAt;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public Instant getLastReadAt() {
        return lastReadAt;
    }

    public void setLastReadAt(Instant lastReadAt) {
        this.lastReadAt = lastReadAt;
    }
}
