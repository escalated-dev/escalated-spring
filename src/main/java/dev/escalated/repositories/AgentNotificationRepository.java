package dev.escalated.repositories;

import dev.escalated.models.AgentNotification;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentNotificationRepository extends JpaRepository<AgentNotification, String> {

    Page<AgentNotification> findByAgentId(Long agentId, Pageable pageable);

    Page<AgentNotification> findByAgentIdAndReadAtIsNull(Long agentId, Pageable pageable);

    long countByAgentIdAndReadAtIsNull(Long agentId);

    long countByAgentId(Long agentId);

    long countByAgentIdAndType(Long agentId, String type);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AgentNotification n SET n.readAt = :now WHERE n.agentId = :agent AND n.id = :id AND n.readAt IS NULL")
    int read(@Param("agent") Long agentId, @Param("id") String id, @Param("now") Instant now);

    @Query("SELECT COUNT(n) FROM AgentNotification n WHERE n.agentId = :agent AND n.id = :id")
    long countMine(@Param("agent") Long agentId, @Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AgentNotification n SET n.readAt = :now WHERE n.agentId = :agent AND n.readAt IS NULL")
    int readAll(@Param("agent") Long agentId, @Param("now") Instant now);
}
