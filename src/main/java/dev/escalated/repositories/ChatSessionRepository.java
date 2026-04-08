package dev.escalated.repositories;

import dev.escalated.models.ChatSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByTicketId(Long ticketId);

    List<ChatSession> findByStatusOrderByCreatedAtAsc(String status);

    List<ChatSession> findByAgentIdAndStatusOrderByLastActivityAtDesc(Long agentId, String status);

    @Query("SELECT s FROM ChatSession s WHERE s.status IN ('waiting', 'active') AND s.lastActivityAt <= :cutoff")
    List<ChatSession> findIdleSessions(@Param("cutoff") Instant cutoff);

    @Query("SELECT COUNT(s) FROM ChatSession s WHERE s.status = 'waiting' AND (:deptId IS NULL OR s.departmentId = :deptId)")
    int countWaiting(@Param("deptId") Long deptId);
}
