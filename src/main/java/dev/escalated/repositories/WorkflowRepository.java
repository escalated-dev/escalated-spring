package dev.escalated.repositories;

import dev.escalated.models.Workflow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {

    List<Workflow> findAllByOrderByPositionAscNameAsc();

    List<Workflow> findByIsActiveTrueOrderByPositionAsc();

    List<Workflow> findByTriggerEventAndIsActiveTrueOrderByPositionAsc(String triggerEvent);
}
