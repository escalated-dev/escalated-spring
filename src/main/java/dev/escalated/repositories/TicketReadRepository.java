package dev.escalated.repositories;

import dev.escalated.models.TicketRead;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketReadRepository extends JpaRepository<TicketRead, Long> {

    List<TicketRead> findByAgentIdAndTicketIdIn(Long agentId, Collection<Long> ticketIds);

    long countByTicketId(Long ticketId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TicketRead r SET r.lastReadAt = :at, r.updatedAt = :now WHERE r.agentId = :agent AND r.ticketId = :ticket")
    int mark(@Param("agent") Long agentId, @Param("ticket") Long ticketId, @Param("at") Instant at, @Param("now") Instant now);
}
