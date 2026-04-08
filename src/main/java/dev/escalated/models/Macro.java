package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "escalated_macros")
public class Macro extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "actions", columnDefinition = "TEXT", nullable = false)
    private String actions;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_shared", nullable = false)
    private boolean shared = true;

    @Column(name = "created_by_agent_id")
    private Long createdByAgentId;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getActions() {
        return actions;
    }

    public void setActions(String actions) {
        this.actions = actions;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public Long getCreatedByAgentId() {
        return createdByAgentId;
    }

    public void setCreatedByAgentId(Long createdByAgentId) {
        this.createdByAgentId = createdByAgentId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
