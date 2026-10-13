package dev.escalated.repositories;

import dev.escalated.models.DepartmentAgent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentAgentRepository extends JpaRepository<DepartmentAgent, Long> {
    boolean existsByDepartmentIdAndAgentId(Long departmentId, Long agentId);
}
