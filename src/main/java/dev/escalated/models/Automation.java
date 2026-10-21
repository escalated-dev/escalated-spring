package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An admin-defined, time-based automation: conditions on open tickets
 * ({@code hours_since_created}, {@code hours_since_updated}, status, ...) and
 * actions to take on each match. Run by
 * {@link dev.escalated.services.AutomationRunner}; never by events (that is
 * what workflows are for).
 */
@Entity
@Table(name = "escalated_automations")
public class Automation extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** A JSON list of {@code {field, operator, value}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON", nullable = false)
    private String conditions;

    /** A JSON list of {@code {type, value}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON", nullable = false)
    private String actions;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int position = 0;

    /** Hours after which the automation may act again on a ticket that still matches. */
    @Column(name = "repeat_after_hours")
    private Integer repeatAfterHours;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Integer getRepeatAfterHours() {
        return repeatAfterHours;
    }

    public void setRepeatAfterHours(Integer repeatAfterHours) {
        this.repeatAfterHours = repeatAfterHours;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }
}
