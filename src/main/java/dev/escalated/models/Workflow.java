package dev.escalated.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "escalated_workflows")
public class Workflow extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotBlank
    @Column(name = "trigger_event", nullable = false)
    private String triggerEvent;

    @Column(columnDefinition = "JSON")
    private String conditions;

    @Column(columnDefinition = "JSON")
    private String actions;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(nullable = false)
    private int position = 0;

    @Column(name = "stop_on_match", nullable = false)
    private boolean stopOnMatch = false;

    @OneToMany(mappedBy = "workflow")
    private List<WorkflowLog> workflowLogs = new ArrayList<>();

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

    @JsonProperty("trigger_event")
    public String getTriggerEvent() {
        return triggerEvent;
    }

    public void setTriggerEvent(String triggerEvent) {
        this.triggerEvent = triggerEvent;
    }

    /** Alias for frontend compatibility: the frontend uses `trigger` instead of `trigger_event`. */
    @JsonProperty("trigger")
    public String getTrigger() {
        return triggerEvent;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    public String getActions() {
        return actions;
    }

    public void setActions(String actions) {
        this.actions = actions;
    }

    @JsonProperty("is_active")
    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @JsonProperty("stop_on_match")
    public boolean isStopOnMatch() {
        return stopOnMatch;
    }

    public void setStopOnMatch(boolean stopOnMatch) {
        this.stopOnMatch = stopOnMatch;
    }

    public List<WorkflowLog> getWorkflowLogs() {
        return workflowLogs;
    }

    public void setWorkflowLogs(List<WorkflowLog> workflowLogs) {
        this.workflowLogs = workflowLogs;
    }
}
