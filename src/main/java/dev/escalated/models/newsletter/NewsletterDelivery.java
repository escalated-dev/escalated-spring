package dev.escalated.models.newsletter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
    name = "escalated_newsletter_deliveries",
    indexes = {
        @Index(name = "idx_nd_nl_status", columnList = "newsletter_id, status"),
        @Index(name = "idx_nd_contact", columnList = "contact_id"),
        @Index(name = "idx_nd_status_claimed", columnList = "status, claimed_at"),
        @Index(name = "uniq_nd_token", columnList = "tracking_token", unique = true),
    }
)
public class NewsletterDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "newsletter_id", nullable = false)
    private Long newsletterId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Column(name = "email_at_send", nullable = false, length = 320)
    private String emailAtSend;

    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Column(name = "tracking_token", nullable = false, length = 40)
    private String trackingToken;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "last_clicked_at")
    private Instant lastClickedAt;

    @Column(name = "clicks_count", nullable = false)
    private int clicksCount = 0;

    @Column(name = "bounce_reason", columnDefinition = "TEXT")
    private String bounceReason;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount = 0;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "is_test", nullable = false)
    private boolean isTest = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getNewsletterId() { return newsletterId; }
    public void setNewsletterId(Long v) { this.newsletterId = v; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long v) { this.contactId = v; }
    public String getEmailAtSend() { return emailAtSend; }
    public void setEmailAtSend(String v) { this.emailAtSend = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getTrackingToken() { return trackingToken; }
    public void setTrackingToken(String v) { this.trackingToken = v; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant v) { this.sentAt = v; }
    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant v) { this.openedAt = v; }
    public Instant getLastClickedAt() { return lastClickedAt; }
    public void setLastClickedAt(Instant v) { this.lastClickedAt = v; }
    public int getClicksCount() { return clicksCount; }
    public void setClicksCount(int v) { this.clicksCount = v; }
    public String getBounceReason() { return bounceReason; }
    public void setBounceReason(String v) { this.bounceReason = v; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String v) { this.failureReason = v; }
    public short getAttemptCount() { return attemptCount; }
    public void setAttemptCount(short v) { this.attemptCount = v; }
    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant v) { this.claimedAt = v; }
    public boolean isTest() { return isTest; }
    public void setTest(boolean v) { this.isTest = v; }
    public Instant getCreatedAt() { return createdAt; }
}
