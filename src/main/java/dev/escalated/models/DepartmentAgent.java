package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Additional memberships, alongside an agent's existing primary department. */
@Entity
@Table(
        name = "escalated_department_agent",
        uniqueConstraints = @UniqueConstraint(columnNames = {"department_id", "agent_id"}))
public class DepartmentAgent extends BaseEntity {
    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    protected DepartmentAgent() {}

    public DepartmentAgent(Long departmentId, Long agentId) {
        this.departmentId = departmentId;
        this.agentId = agentId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public Long getAgentId() {
        return agentId;
    }
}
