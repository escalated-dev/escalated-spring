package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An emailed guest verification code. Only digests are stored; see
 * {@link dev.escalated.services.GuestVerificationService}.
 */
@Entity
@Table(name = "escalated_guest_verifications")
public class GuestVerification extends BaseEntity {

    @Column(name = "public_id", nullable = false, length = 64, unique = true)
    private String publicId;

    @Column(name = "email_digest", nullable = false, length = 64)
    private String emailDigest;

    @Column(name = "client_digest", nullable = false, length = 64)
    private String clientDigest;

    @Column(nullable = false, length = 16)
    private String purpose;

    @Column(name = "code_digest", nullable = false, length = 64)
    private String codeDigest;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getEmailDigest() {
        return emailDigest;
    }

    public void setEmailDigest(String emailDigest) {
        this.emailDigest = emailDigest;
    }

    public String getClientDigest() {
        return clientDigest;
    }

    public void setClientDigest(String clientDigest) {
        this.clientDigest = clientDigest;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getCodeDigest() {
        return codeDigest;
    }

    public void setCodeDigest(String codeDigest) {
        this.codeDigest = codeDigest;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}
