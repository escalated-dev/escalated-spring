package dev.escalated.repositories;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.ImportJob;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, String> {

    List<ImportJob> findAllByOrderByCreatedAtDesc(Pageable page);

    @Query("SELECT j.status FROM ImportJob j WHERE j.id = :id")
    String statusOf(@Param("id") String id);

    /** Progress and errors only: the status is the admin's to change while a run goes. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ImportJob j SET j.progress = :progress, j.errorLog = :errors, j.updatedAt = :now"
            + " WHERE j.id = :id")
    int saveProgress(@Param("id") String id, @Param("progress") String progress, @Param("errors") String errors,
                     @Param("now") Instant now);

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ImportJob j SET j.status = :status WHERE j.id = :id")
    int setStatus(@Param("id") String id, @Param("status") String status);
}
