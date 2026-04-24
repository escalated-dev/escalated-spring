package dev.escalated.repositories;

import dev.escalated.models.DeferredWorkflowJob;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeferredWorkflowJobRepository extends JpaRepository<DeferredWorkflowJob, Long> {

    List<DeferredWorkflowJob> findByStatusAndRunAtLessThanEqual(String status, Instant runAt);
}
