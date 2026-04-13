package dev.escalated.repositories;

import dev.escalated.models.WorkflowLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowLogRepository extends JpaRepository<WorkflowLog, Long> {

    @Query("SELECT l FROM WorkflowLog l JOIN FETCH l.workflow JOIN FETCH l.ticket WHERE l.workflow.id = :workflowId ORDER BY l.createdAt DESC")
    List<WorkflowLog> findByWorkflowIdWithRelations(@Param("workflowId") Long workflowId);
}
