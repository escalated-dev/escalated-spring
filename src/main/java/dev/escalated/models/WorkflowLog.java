package dev.escalated.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Entity
@Table(name = "escalated_workflow_logs")
public class WorkflowLog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    @JsonIgnore
    private Workflow workflow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    @JsonIgnore
    private Ticket ticket;

    @Column(name = "trigger_event", nullable = false)
    private String triggerEvent;

    @Column(name = "conditions_matched", nullable = false)
    private boolean conditionsMatched = true;

    @Column(name = "actions_executed", columnDefinition = "JSON")
    private String actionsExecutedJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // --- Getters/Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    @JsonProperty("trigger_event")
    public String getTriggerEvent() {
        return triggerEvent;
    }

    public void setTriggerEvent(String triggerEvent) {
        this.triggerEvent = triggerEvent;
    }

    public boolean isConditionsMatched() {
        return conditionsMatched;
    }

    public void setConditionsMatched(boolean conditionsMatched) {
        this.conditionsMatched = conditionsMatched;
    }

    @JsonIgnore
    public String getActionsExecutedJson() {
        return actionsExecutedJson;
    }

    public void setActionsExecutedJson(String actionsExecutedJson) {
        this.actionsExecutedJson = actionsExecutedJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    @JsonProperty("created_at")
    public Instant getCreatedAt() {
        return createdAt;
    }

    // --- Computed fields expected by the frontend ---

    /** Alias: frontend reads `event` instead of `trigger_event`. */
    @JsonProperty("event")
    public String getEvent() {
        return triggerEvent;
    }

    /** Frontend reads `workflow_name` from eager-loaded relationship. */
    @JsonProperty("workflow_name")
    public String getWorkflowName() {
        return workflow != null ? workflow.getName() : null;
    }

    /** Frontend reads `ticket_reference` from eager-loaded relationship. */
    @JsonProperty("ticket_reference")
    public String getTicketReference() {
        return ticket != null ? ticket.getTicketNumber() : null;
    }

    /** Boolean alias for conditions_matched. */
    @JsonProperty("matched")
    public boolean isMatched() {
        return conditionsMatched;
    }

    /** Integer count of executed actions. */
    @JsonProperty("actions_executed")
    public int getActionsExecutedCount() {
        List<Object> list = parseActionsExecuted();
        return list != null ? list.size() : 0;
    }

    /** Raw actions array for the expanded detail view. */
    @JsonProperty("action_details")
    public List<Object> getActionDetails() {
        List<Object> list = parseActionsExecuted();
        return list != null ? list : List.of();
    }

    /** Milliseconds between started_at and completed_at. */
    @JsonProperty("duration_ms")
    public Long getDurationMs() {
        if (startedAt != null && completedAt != null) {
            return completedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
        return null;
    }

    /** Computed status: 'failed' when an error is present, otherwise 'success'. */
    @JsonProperty("status")
    public String getStatus() {
        return (errorMessage != null && !errorMessage.isEmpty()) ? "failed" : "success";
    }

    @SuppressWarnings("unchecked")
    private List<Object> parseActionsExecuted() {
        if (actionsExecutedJson == null || actionsExecutedJson.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(actionsExecutedJson, new TypeReference<List<Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
