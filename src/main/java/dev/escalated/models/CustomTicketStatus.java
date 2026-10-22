package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * An admin-defined ticket status: a named sub-status of one of five categories ({@code new},
 * {@code open}, {@code pending}, {@code on_hold}, {@code solved}).
 *
 * <p>Named apart from {@link TicketStatus}, the built-in enum, which stays the ticket's lifecycle
 * status. A ticket given a custom status takes its category's built-in anchor as its status and
 * points at the custom one with {@code ticket_status_id}; all lifecycle logic keys off the anchor.
 * System rows ({@code is_system}) mirror the built-in statuses so the admin page can show and
 * label them; their slug and category never change and they cannot be deleted.
 */
@Entity
@Table(name = "escalated_ticket_statuses")
public class CustomTicketStatus extends BaseEntity {

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false, length = 20)
    private String category = "open";

    @Column(nullable = false, length = 20)
    private String color = "#6b7280";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_default", nullable = false)
    private boolean defaultStatus;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isDefaultStatus() {
        return defaultStatus;
    }

    public void setDefaultStatus(boolean defaultStatus) {
        this.defaultStatus = defaultStatus;
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }
}
