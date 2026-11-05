package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * When a customer was last seen on the customer portal or the widget (ADR
 * 2026-11-05-agent-panel-data), keyed by their lower-cased email: the identity tickets carry for
 * their requester. Only agent pages ever read it.
 */
@Entity
@Table(name = "escalated_customer_presence")
public class CustomerPresence extends BaseEntity {

    @Column(nullable = false, unique = true, length = 191)
    private String email;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected CustomerPresence() {}

    public CustomerPresence(String email, Instant lastSeenAt) {
        this.email = email;
        this.lastSeenAt = lastSeenAt;
    }

    public String getEmail() {
        return email;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
