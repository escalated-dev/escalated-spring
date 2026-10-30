package dev.escalated.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A staff user's TOTP enrolment (ADR 2026-10-29-configurable-access-features): pending until a code
 * confirms it. The secret and the recovery codes are stored encrypted and never serialised.
 */
@Entity
@Table(name = "escalated_two_factors")
public class TwoFactor extends BaseEntity {

    /** The agent profile it belongs to: one enrolment each. */
    @Column(name = "agent_id", nullable = false, unique = true)
    private Long agentId;

    @JsonIgnore
    @Column(name = "secret", nullable = false, columnDefinition = "TEXT")
    private String secret;

    /** The unused recovery codes, a JSON array, encrypted. */
    @JsonIgnore
    @Column(name = "recovery_codes", columnDefinition = "TEXT")
    private String recoveryCodes;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** The time step of the last code accepted: that step and earlier ones are refused. */
    @Column(name = "last_used_step")
    private Long lastUsedStep;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getRecoveryCodes() {
        return recoveryCodes;
    }

    public void setRecoveryCodes(String recoveryCodes) {
        this.recoveryCodes = recoveryCodes;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    public Long getLastUsedStep() {
        return lastUsedStep;
    }

    public void setLastUsedStep(Long lastUsedStep) {
        this.lastUsedStep = lastUsedStep;
    }
}
