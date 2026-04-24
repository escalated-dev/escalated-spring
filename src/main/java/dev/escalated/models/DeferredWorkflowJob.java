package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Queue row for a paused workflow run — populated by the
 * {@code delay} workflow action when execution hits a wait clause
 * and consumed by
 * {@link dev.escalated.services.WorkflowExecutorService#runDueDeferredJobs}
 * to resume.
 *
 * <p>Rows are soft-terminal: the poller flips {@code status} to
 * {@code done} (or {@code failed}) after running so they don't get
 * re-picked up, and retains the row for audit.
 */
@Entity
@Table(
        name = "escalated_deferred_workflow_jobs",
        indexes = @Index(name = "idx_deferred_status_runat", columnList = "status,run_at"))
public class DeferredWorkflowJob extends BaseEntity {

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /**
     * Remaining actions to run after the delay expires. Stored as a
     * JSON array mirroring the shape of {@code Workflow.actions}.
     */
    @Column(name = "remaining_actions", columnDefinition = "JSON", nullable = false)
    private String remainingActionsJson;

    /** UTC timestamp after which the poller should pick this row up. */
    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    /** {@code pending} | {@code done} | {@code failed} — soft state machine. */
    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getRemainingActionsJson() {
        return remainingActionsJson;
    }

    public void setRemainingActionsJson(String remainingActionsJson) {
        this.remainingActionsJson = remainingActionsJson;
    }

    public Instant getRunAt() {
        return runAt;
    }

    public void setRunAt(Instant runAt) {
        this.runAt = runAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
