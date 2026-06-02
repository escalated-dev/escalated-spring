package dev.escalated.models.newsletter;

import dev.escalated.models.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "escalated_newsletter_list_members",
    uniqueConstraints = @UniqueConstraint(name = "uniq_nlm_list_contact", columnNames = {"list_id", "contact_id"}),
    indexes = @Index(name = "idx_nlm_contact", columnList = "contact_id")
)
public class NewsletterListMember extends BaseEntity {

    @Column(name = "list_id", nullable = false)
    private Long listId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    @Column(name = "added_by")
    private String addedBy;

    public Long getListId() { return listId; }
    public void setListId(Long listId) { this.listId = listId; }
    public Long getContactId() { return contactId; }
    public void setContactId(Long contactId) { this.contactId = contactId; }
    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
    public String getAddedBy() { return addedBy; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }
}
