package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * When a sweep rule (an escalation rule, an automation, an SLA warning or
 * breach) last acted on a ticket. One row per (rule type, rule, ticket); see
 * {@link dev.escalated.services.RuleFireLog}.
 */
@Entity
@Table(
        name = "escalated_rule_executions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_escalated_rule_executions_rule_ticket",
                columnNames = {"rule_type", "rule_id", "ticket_id"}))
public class RuleExecution extends BaseEntity {

    public static final String ESCALATION_RULE = "escalation_rule";
    public static final String AUTOMATION = "automation";
    public static final String SLA_WARNING = "sla_warning";
    public static final String SLA_BREACH = "sla_breach";

    /** The rule id {@link #SLA_WARNING} and {@link #SLA_BREACH} rows use for the first-response deadline. */
    public static final long FIRST_RESPONSE = 1L;
    /** The rule id {@link #SLA_WARNING} and {@link #SLA_BREACH} rows use for the resolution deadline. */
    public static final long RESOLUTION = 2L;

    @Column(name = "rule_type", nullable = false, length = 32)
    private String ruleType;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "fired_at", nullable = false)
    private Instant firedAt;

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Instant getFiredAt() {
        return firedAt;
    }

    public void setFiredAt(Instant firedAt) {
        this.firedAt = firedAt;
    }
}
