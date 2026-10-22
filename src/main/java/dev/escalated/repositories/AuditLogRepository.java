package dev.escalated.repositories;

import dev.escalated.models.AuditLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** The actions recorded so far, for the audit log page's filter. */
    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> distinctActions();

    /** The entity types recorded so far, for the audit log page's filter. */
    @Query("SELECT DISTINCT a.entityType FROM AuditLog a ORDER BY a.entityType")
    List<String> distinctEntityTypes();

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    Page<AuditLog> findByActorEmailOrderByCreatedAtDesc(String actorEmail, Pageable pageable);
}
