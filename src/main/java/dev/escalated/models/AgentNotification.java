package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An agent's in-app notification (ADR 2026-11-05-agent-panel-data). {@code data} is JSON with the
 * ticket id and what the title needs; the feed builds each item when it is read, so a ticket the
 * agent can no longer view is redacted.
 */
@Entity
@Table(name = "escalated_notifications",
        indexes = @Index(name = "idx_escalated_notifications_agent", columnList = "agent_id, read_at"))
public class AgentNotification {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String data;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentNotification() {}

    public AgentNotification(String id, Long agentId, String type, String data, Instant createdAt) {
        this.id = id;
        this.agentId = agentId;
        this.type = type;
        this.data = data;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public Long getAgentId() {
        return agentId;
    }

    public String getType() {
        return type;
    }

    public String getData() {
        return data;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
