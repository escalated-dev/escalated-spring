package dev.escalated.repositories;

import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    Optional<Ticket> findByGuestAccessToken(String token);

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    Page<Ticket> findByPriority(TicketPriority priority, Pageable pageable);

    Page<Ticket> findByRequesterEmail(String email, Pageable pageable);

    Page<Ticket> findByAssignedAgentId(Long agentId, Pageable pageable);

    Page<Ticket> findByDepartmentId(Long departmentId, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.status = 'SNOOZED' AND t.snoozedUntil <= :now")
    List<Ticket> findSnoozedTicketsDue(@Param("now") Instant now);

    @Query("SELECT t FROM Ticket t WHERE t.slaDueAt IS NOT NULL AND t.slaDueAt <= :now AND t.status NOT IN ('CLOSED', 'RESOLVED', 'MERGED')")
    List<Ticket> findTicketsBreachingSla(@Param("now") Instant now);

    @Query("SELECT t FROM Ticket t WHERE t.slaFirstResponseDueAt IS NOT NULL AND t.firstRespondedAt IS NULL "
            + "AND t.slaFirstResponseDueAt <= :now AND t.status NOT IN ('CLOSED', 'RESOLVED', 'MERGED')")
    List<Ticket> findTicketsBreachingFirstResponse(@Param("now") Instant now);

    @Query("SELECT t FROM Ticket t WHERE t.status NOT IN ('CLOSED', 'RESOLVED', 'MERGED') "
            + "AND t.assignedAgent IS NULL")
    List<Ticket> findUnassignedTickets();

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.assignedAgent.id = :agentId AND t.status NOT IN ('CLOSED', 'RESOLVED', 'MERGED')")
    int countActiveTicketsByAgent(@Param("agentId") Long agentId);

    @Query("SELECT t FROM Ticket t WHERE t.requesterEmail = :email AND t.status NOT IN ('CLOSED', 'MERGED') ORDER BY t.createdAt DESC")
    List<Ticket> findOpenTicketsByRequester(@Param("email") String email);

    @Query("SELECT t FROM Ticket t WHERE t.emailMessageId = :messageId")
    Optional<Ticket> findByEmailMessageId(@Param("messageId") String messageId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = :status")
    long countByStatus(@Param("status") TicketStatus status);
}
