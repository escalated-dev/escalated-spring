package dev.escalated.models.newsletter;

import dev.escalated.models.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

@Entity
@Table(
    name = "escalated_newsletters",
    indexes = {
        @Index(name = "idx_n_status", columnList = "status"),
        @Index(name = "idx_n_scheduled_at", columnList = "scheduled_at"),
        @Index(name = "idx_n_status_sched", columnList = "status, scheduled_at"),
        @Index(name = "idx_n_created_by", columnList = "created_by"),
    }
)
public class Newsletter extends BaseEntity {

    @NotBlank
    @Column(nullable = false, length = 998)
    private String subject;

    @NotBlank
    @Email
    @Column(name = "from_email", nullable = false, length = 320)
    private String fromEmail;

    @Column(name = "from_name")
    private String fromName;

    @Email
    @Column(name = "reply_to", length = 320)
    private String replyTo;

    @Column(name = "target_list_id", nullable = false)
    private Long targetListId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(length = 64)
    private String theme;

    @Column(name = "body_markdown", columnDefinition = "TEXT")
    private String bodyMarkdown;

    @Column(nullable = false, length = 16)
    private String status = "draft";

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "sent_by")
    private String sentBy;

    @Column(name = "summary_total", nullable = false)
    private int summaryTotal = 0;

    @Column(name = "summary_sent", nullable = false)
    private int summarySent = 0;

    @Column(name = "summary_opened", nullable = false)
    private int summaryOpened = 0;

    @Column(name = "summary_clicked", nullable = false)
    private int summaryClicked = 0;

    @Column(name = "summary_bounced", nullable = false)
    private int summaryBounced = 0;

    @Column(name = "summary_complained", nullable = false)
    private int summaryComplained = 0;

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String fromEmail) { this.fromEmail = fromEmail; }
    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }
    public String getReplyTo() { return replyTo; }
    public void setReplyTo(String replyTo) { this.replyTo = replyTo; }
    public Long getTargetListId() { return targetListId; }
    public void setTargetListId(Long targetListId) { this.targetListId = targetListId; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getBodyMarkdown() { return bodyMarkdown; }
    public void setBodyMarkdown(String bodyMarkdown) { this.bodyMarkdown = bodyMarkdown; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public int getSummaryTotal() { return summaryTotal; }
    public void setSummaryTotal(int v) { this.summaryTotal = v; }
    public int getSummarySent() { return summarySent; }
    public void incrementSummarySent() { this.summarySent++; }
    public int getSummaryOpened() { return summaryOpened; }
    public void incrementSummaryOpened() { this.summaryOpened++; }
    public int getSummaryClicked() { return summaryClicked; }
    public void incrementSummaryClicked() { this.summaryClicked++; }
    public int getSummaryBounced() { return summaryBounced; }
    public void incrementSummaryBounced() { this.summaryBounced++; }
    public int getSummaryComplained() { return summaryComplained; }
    public void incrementSummaryComplained() { this.summaryComplained++; }
}
