package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * An SSO message ID this install issued or accepted (ADR 2026-10-29-configurable-access-features):
 * a pending SAML AuthnRequest ({@code saml_request}), an accepted assertion ({@code saml_assertion})
 * or a JWT {@code jti} ({@code jwt}), kept until {@code expires_at} (Unix seconds).
 */
@Entity
@Table(name = "escalated_sso_message_ids",
        uniqueConstraints = @UniqueConstraint(name = "uq_escalated_sso_message_ids", columnNames = {"kind", "message_id"}))
public class SsoMessageId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kind", nullable = false, length = 32)
    private String kind;

    @Column(name = "message_id", nullable = false, length = 191)
    private String messageId;

    @Column(name = "expires_at", nullable = false)
    private long expiresAt;

    public Long getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
