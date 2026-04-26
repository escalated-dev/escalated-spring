package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * First-class identity for guest requesters (Pattern B).
 *
 * <p>Deduped by email (unique index; value is lowercased + trimmed
 * before persistence via {@code normalizeEmail}). Links to a
 * host-app user via {@code userId} once the guest accepts a
 * signup invite.
 *
 * <p>Spring is greenfield for public ticketing — unlike the other
 * frameworks, there are no pre-existing inline {@code guest_*}
 * fields to preserve. This implementation goes straight to Pattern B.
 *
 * @see <a href="https://github.com/escalated-dev/escalated/blob/feat/public-ticket-system/docs/superpowers/plans/2026-04-24-public-tickets-rollout-status.md">rollout status</a>
 */
@Entity
@Table(
    name = "escalated_contacts",
    indexes = {
        @Index(name = "idx_contact_user", columnList = "user_id"),
    }
)
public class Contact extends BaseEntity {

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(length = 255)
    private String name;

    /** Linked host-app user id once the contact creates an account. */
    @Column(name = "user_id")
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    // -----------------------------------------------------------------
    // Pure static helpers — used by the service layer and testable
    // without a JPA context.
    // -----------------------------------------------------------------

    /**
     * Canonical email normalization: trim surrounding whitespace and
     * lowercase. Always call on any caller-supplied email before
     * inserting or looking up.
     */
    public static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    /** Decision outcome for {@link #decideAction}. */
    public enum Action {
        CREATE,
        UPDATE_NAME,
        RETURN_EXISTING,
    }

    /**
     * Pure branch-selection helper for find-or-create-by-email.
     * Returns:
     * <ul>
     *   <li>{@link Action#CREATE} when no existing row matched</li>
     *   <li>{@link Action#UPDATE_NAME} when existing has a blank
     *       name and a non-blank name was supplied</li>
     *   <li>{@link Action#RETURN_EXISTING} otherwise</li>
     * </ul>
     */
    public static Action decideAction(Contact existing, String incomingName) {
        if (existing == null) {
            return Action.CREATE;
        }
        String existingName = existing.getName();
        boolean existingBlank = existingName == null || existingName.isEmpty();
        boolean incomingPresent = incomingName != null && !incomingName.isEmpty();
        if (existingBlank && incomingPresent) {
            return Action.UPDATE_NAME;
        }
        return Action.RETURN_EXISTING;
    }

    @PrePersist
    private void normalizeBeforePersist() {
        if (this.email != null) {
            this.email = normalizeEmail(this.email);
        }
    }
}
