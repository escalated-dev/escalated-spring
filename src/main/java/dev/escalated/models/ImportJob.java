package dev.escalated.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One import from an external platform, as escalated-laravel's {@code ImportJob}: its status,
 * per-entity progress and cursors (JSON), error log (JSON) and the source credentials, sealed by
 * {@code ImportCredentials} and dropped when the import completes or is cancelled.
 */
@Entity
@Table(name = "escalated_import_jobs", indexes = @Index(name = "idx_escalated_import_jobs_status", columnList = "status"))
public class ImportJob {

    /** escalated-laravel's state machine. */
    public static final Map<String, List<String>> TRANSITIONS = Map.of(
            "pending", List.of("authenticating"),
            "authenticating", List.of("mapping", "failed"),
            "mapping", List.of("importing", "failed"),
            "importing", List.of("paused", "completed", "failed"),
            "paused", List.of("importing", "failed"),
            "completed", List.of(),
            "failed", List.of("mapping"));

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(nullable = false, length = 30)
    private String status = "pending";

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String credentials;

    @Column(name = "field_mappings", columnDefinition = "TEXT")
    private String fieldMappings;

    @Column(columnDefinition = "TEXT")
    private String progress;

    @Column(name = "error_log", columnDefinition = "TEXT")
    private String errorLog;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Moves along the state machine; throws on a transition it does not allow. */
    public void transitionTo(String next) {
        if (!TRANSITIONS.getOrDefault(status, List.of()).contains(next)) {
            throw new IllegalStateException("Cannot transition from '" + status + "' to '" + next + "'.");
        }
        status = next;
    }

    public boolean isResumable() {
        return "paused".equals(status) || "failed".equals(status);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCredentials() {
        return credentials;
    }

    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }

    public String getFieldMappings() {
        return fieldMappings;
    }

    public void setFieldMappings(String fieldMappings) {
        this.fieldMappings = fieldMappings;
    }

    public String getProgress() {
        return progress;
    }

    public void setProgress(String progress) {
        this.progress = progress;
    }

    public String getErrorLog() {
        return errorLog;
    }

    public void setErrorLog(String errorLog) {
        this.errorLog = errorLog;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
