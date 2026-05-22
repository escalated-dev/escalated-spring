package dev.escalated.models.newsletter;

import dev.escalated.models.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(
    name = "escalated_newsletter_templates",
    indexes = {
        @Index(name = "idx_nlt_theme", columnList = "theme"),
        @Index(name = "idx_nlt_created_by", columnList = "created_by"),
    }
)
public class NewsletterTemplate extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 64)
    private String theme = "default";

    @Column(name = "subject_template", length = 998)
    private String subjectTemplate;

    @NotBlank
    @Column(name = "body_markdown", nullable = false, columnDefinition = "TEXT")
    private String bodyMarkdown;

    @Column(name = "merge_fields_schema", columnDefinition = "TEXT")
    private String mergeFieldsSchema;

    @Column(name = "created_by")
    private Long createdBy;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getSubjectTemplate() { return subjectTemplate; }
    public void setSubjectTemplate(String subjectTemplate) { this.subjectTemplate = subjectTemplate; }
    public String getBodyMarkdown() { return bodyMarkdown; }
    public void setBodyMarkdown(String bodyMarkdown) { this.bodyMarkdown = bodyMarkdown; }
    public String getMergeFieldsSchema() { return mergeFieldsSchema; }
    public void setMergeFieldsSchema(String mergeFieldsSchema) { this.mergeFieldsSchema = mergeFieldsSchema; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
