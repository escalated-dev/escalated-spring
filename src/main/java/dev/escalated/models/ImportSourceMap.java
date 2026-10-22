package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Which Escalated record a source record became, per import job. A record already mapped is
 * skipped, which is what makes an import resumable; later entity types resolve their references
 * (a reply's ticket) through it.
 */
@Entity
@Table(name = "escalated_import_source_maps", uniqueConstraints = @UniqueConstraint(
        name = "uq_escalated_import_source_maps", columnNames = {"import_job_id", "entity_type", "source_id"}))
public class ImportSourceMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_job_id", nullable = false, length = 36)
    private String importJobId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "source_id", nullable = false, length = 191)
    private String sourceId;

    @Column(name = "escalated_id", nullable = false, length = 191)
    private String escalatedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImportSourceMap() {
    }

    public ImportSourceMap(String importJobId, String entityType, String sourceId, String escalatedId) {
        this.importJobId = importJobId;
        this.entityType = entityType;
        this.sourceId = sourceId;
        this.escalatedId = escalatedId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getImportJobId() {
        return importJobId;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getEscalatedId() {
        return escalatedId;
    }
}
