package dev.escalated.models.newsletter;

import dev.escalated.models.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(
    name = "escalated_newsletter_lists",
    indexes = {
        @Index(name = "idx_nl_kind", columnList = "kind"),
        @Index(name = "idx_nl_created_by", columnList = "created_by"),
    }
)
public class NewsletterList extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** "static" | "dynamic" */
    @NotBlank
    @Column(nullable = false, length = 16)
    private String kind = "static";

    /** Serialized filter rules JSON. Use {@code @Convert} in production. */
    @Column(name = "filter_json", columnDefinition = "TEXT")
    private String filterJson;

    @Column(name = "created_by")
    private Long createdBy;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getFilterJson() { return filterJson; }
    public void setFilterJson(String filterJson) { this.filterJson = filterJson; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
