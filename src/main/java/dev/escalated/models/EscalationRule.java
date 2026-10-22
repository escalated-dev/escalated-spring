package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * An escalation rule as escalated-laravel's: standalone, run by the SLA sweep against every open
 * ticket matching all its {@code conditions} (a JSON list of {@code {field, value}}), on which it
 * performs its {@code actions} (a JSON list of {@code {type, value}}), once per ticket.
 */
@Entity
@Table(name = "escalated_escalation_rules")
public class EscalationRule extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String category = "Uncategorized";

    /** Descriptive, as on Laravel: the conditions decide when a rule acts. */
    @Column(name = "trigger_type", nullable = false)
    private String triggerType = "time_based";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String conditions = "[]";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String actions = "[]";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Hours after which the rule may act again on a ticket that still
     * matches. Null (the default) means once, until the ticket stops matching.
     */
    @Column(name = "repeat_after_hours")
    private Integer repeatAfterHours;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    /** The conditions as JSON: {@code [{"field": "priority", "value": "high"}, ...]}. */
    public String getConditions() {
        return conditions;
    }

    public void setConditions(String value) {
        conditions = value == null ? "[]" : value;
    }

    /** The actions as JSON: {@code [{"type": "assign_to", "value": "12"}, ...]}. */
    public String getActions() {
        return actions;
    }

    public void setActions(String value) {
        actions = value == null ? "[]" : value;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getRepeatAfterHours() {
        return repeatAfterHours;
    }

    public void setRepeatAfterHours(Integer repeatAfterHours) {
        this.repeatAfterHours = repeatAfterHours;
    }
}
