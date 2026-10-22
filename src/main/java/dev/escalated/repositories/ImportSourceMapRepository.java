package dev.escalated.repositories;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.ImportSourceMap;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ImportSourceMapRepository extends JpaRepository<ImportSourceMap, Long> {

    Optional<ImportSourceMap> findByImportJobIdAndEntityTypeAndSourceId(String importJobId, String entityType,
                                                                       String sourceId);

    List<ImportSourceMap> findByImportJobIdAndEntityTypeOrderByIdAsc(String importJobId, String entityType);

    /** An imported ticket keeps the source's creation time (the entity stamps its own on insert). */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Ticket t SET t.createdAt = :at WHERE t.id = :id")
    int keepTicketCreatedAt(@Param("id") Long id, @Param("at") Instant at);

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Reply r SET r.createdAt = :at WHERE r.id = :id")
    int keepReplyCreatedAt(@Param("id") Long id, @Param("at") Instant at);
}
