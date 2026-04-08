package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "escalated_saved_views")
public class SavedView extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(name = "filters", columnDefinition = "TEXT", nullable = false)
    private String filters;

    @Column(name = "sort_by")
    private String sortBy;

    @Column(name = "sort_direction")
    private String sortDirection = "desc";

    @Column(name = "columns", columnDefinition = "TEXT")
    private String columns;

    @Column(name = "is_shared", nullable = false)
    private boolean shared = false;

    @Column(name = "is_default", nullable = false)
    private boolean defaultView = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private AgentProfile agent;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFilters() {
        return filters;
    }

    public void setFilters(String filters) {
        this.filters = filters;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    public String getColumns() {
        return columns;
    }

    public void setColumns(String columns) {
        this.columns = columns;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public boolean isDefaultView() {
        return defaultView;
    }

    public void setDefaultView(boolean defaultView) {
        this.defaultView = defaultView;
    }

    public AgentProfile getAgent() {
        return agent;
    }

    public void setAgent(AgentProfile agent) {
        this.agent = agent;
    }
}
