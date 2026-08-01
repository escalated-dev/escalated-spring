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

    /**
     * Shared JPQL predicate: a ticket has breached its SLA when a first
     * response landed late, is overdue with no response yet, resolution
     * landed late, or the ticket is still open past its resolution due time.
     * Bound parameter {@code :now} must be supplied by any query using it.
     */
    String SLA_BREACH_PREDICATE =
            "((t.firstRespondedAt IS NOT NULL AND t.slaFirstResponseDueAt IS NOT NULL "
            + "AND t.firstRespondedAt > t.slaFirstResponseDueAt) "
            + "OR (t.firstRespondedAt IS NULL AND t.slaFirstResponseDueAt IS NOT NULL "
            + "AND t.slaFirstResponseDueAt < :now) "
            + "OR (t.resolvedAt IS NOT NULL AND t.slaDueAt IS NOT NULL AND t.resolvedAt > t.slaDueAt) "
            + "OR (t.resolvedAt IS NULL AND t.slaDueAt IS NOT NULL AND t.slaDueAt < :now "
            + "AND t.status NOT IN ('CLOSED', 'RESOLVED', 'MERGED')))";

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

    long countByRequesterEmail(String requesterEmail);

    // ── Reporting aggregations ───────────────────────────────────────────────

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createdAt >= :since")
    long countCreatedSince(@Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createdAt >= :start AND t.createdAt < :end")
    long countCreatedBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.resolvedAt IS NOT NULL AND t.resolvedAt >= :since")
    long countResolvedSince(@Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.resolvedAt IS NOT NULL "
            + "AND t.resolvedAt >= :start AND t.resolvedAt < :end")
    long countResolvedBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT t.status, COUNT(t) FROM Ticket t WHERE t.createdAt >= :since GROUP BY t.status")
    List<Object[]> countByStatusSince(@Param("since") Instant since);

    @Query("SELECT t.priority, COUNT(t) FROM Ticket t WHERE t.createdAt >= :since GROUP BY t.priority")
    List<Object[]> countByPrioritySince(@Param("since") Instant since);

    @Query("SELECT t.channel, COUNT(t) FROM Ticket t WHERE t.createdAt >= :since GROUP BY t.channel")
    List<Object[]> countByChannelSince(@Param("since") Instant since);

    @Query("SELECT t.createdAt FROM Ticket t WHERE t.createdAt >= :since")
    List<Instant> createdAtSince(@Param("since") Instant since);

    @Query("SELECT t.createdAt, t.firstRespondedAt FROM Ticket t "
            + "WHERE t.createdAt >= :since AND t.firstRespondedAt IS NOT NULL")
    List<Object[]> firstResponseTimings(@Param("since") Instant since);

    @Query("SELECT t.createdAt, t.resolvedAt FROM Ticket t "
            + "WHERE t.createdAt >= :since AND t.resolvedAt IS NOT NULL")
    List<Object[]> resolutionTimings(@Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createdAt >= :since AND t.slaPolicy IS NOT NULL")
    long countWithSlaSince(@Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createdAt >= :since AND t.slaFirstResponseDueAt IS NOT NULL "
            + "AND ((t.firstRespondedAt IS NOT NULL AND t.firstRespondedAt > t.slaFirstResponseDueAt) "
            + "OR (t.firstRespondedAt IS NULL AND t.slaFirstResponseDueAt < :now))")
    long countFirstResponseBreaches(@Param("since") Instant since, @Param("now") Instant now);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createdAt >= :since AND t.slaDueAt IS NOT NULL "
            + "AND ((t.resolvedAt IS NOT NULL AND t.resolvedAt > t.slaDueAt) "
            + "OR (t.resolvedAt IS NULL AND t.slaDueAt < :now "
            + "AND t.status NOT IN ('CLOSED', 'RESOLVED', 'MERGED')))")
    long countResolutionBreaches(@Param("since") Instant since, @Param("now") Instant now);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createdAt >= :since AND t.slaPolicy IS NOT NULL "
            + "AND " + SLA_BREACH_PREDICATE)
    long countSlaBreaches(@Param("since") Instant since, @Param("now") Instant now);

    @Query("SELECT t.priority, COUNT(t) FROM Ticket t "
            + "WHERE t.createdAt >= :since AND t.slaPolicy IS NOT NULL GROUP BY t.priority")
    List<Object[]> countSlaTicketsByPriority(@Param("since") Instant since);

    @Query("SELECT t.priority, COUNT(t) FROM Ticket t WHERE t.createdAt >= :since AND t.slaPolicy IS NOT NULL "
            + "AND " + SLA_BREACH_PREDICATE + " GROUP BY t.priority")
    List<Object[]> countSlaBreachesByPriority(@Param("since") Instant since, @Param("now") Instant now);

    @Query("SELECT a.id, a.name, COUNT(t), SUM(CASE WHEN t.resolvedAt IS NOT NULL THEN 1 ELSE 0 END) "
            + "FROM Ticket t JOIN t.assignedAgent a WHERE t.createdAt >= :since GROUP BY a.id, a.name")
    List<Object[]> agentTicketCounts(@Param("since") Instant since);

    @Query("SELECT a.id, t.createdAt, t.firstRespondedAt FROM Ticket t JOIN t.assignedAgent a "
            + "WHERE t.createdAt >= :since AND t.firstRespondedAt IS NOT NULL")
    List<Object[]> agentFirstResponseTimings(@Param("since") Instant since);
}
