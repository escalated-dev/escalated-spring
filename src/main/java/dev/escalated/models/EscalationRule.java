package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "escalated_escalation_rules")
public class EscalationRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_policy_id", nullable = false)
    private SlaPolicy slaPolicy;

    @Column(nullable = false)
    private String name;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType = "sla_breach";

    @Column(name = "minutes_before_or_after", nullable = false)
    private int minutesBeforeOrAfter = 0;

    @Column(name = "action_type", nullable = false)
    private String actionType = "reassign";

    @Column(name = "action_target")
    private String actionTarget;

    @Column(name = "notify_emails")
    private String notifyEmails;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public SlaPolicy getSlaPolicy() {
        return slaPolicy;
    }

    public void setSlaPolicy(SlaPolicy slaPolicy) {
        this.slaPolicy = slaPolicy;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public int getMinutesBeforeOrAfter() {
        return minutesBeforeOrAfter;
    }

    public void setMinutesBeforeOrAfter(int minutesBeforeOrAfter) {
        this.minutesBeforeOrAfter = minutesBeforeOrAfter;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getActionTarget() {
        return actionTarget;
    }

    public void setActionTarget(String actionTarget) {
        this.actionTarget = actionTarget;
    }

    public String getNotifyEmails() {
        return notifyEmails;
    }

    public void setNotifyEmails(String notifyEmails) {
        this.notifyEmails = notifyEmails;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
