package dev.escalated.repositories;

import dev.escalated.models.Ticket;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Aggregates for the analytics report screens (SLA trends, response and resolution times, agent
 * ranking, cohorts). Counts are grouped and summed in the database; the hour figures come back as
 * timestamp pairs, since the hours between two timestamps is not portable SQL and the medians and
 * 90th percentiles need every value anyway.
 *
 * <p>A breach is what the other report screens count: the first response or the resolution came
 * after its due time, or has not come and the ticket is still open past it.
 */
public interface TicketReportRepository extends Repository<Ticket, Long> {

    String OPEN = "t.status NOT IN ('RESOLVED', 'CLOSED', 'MERGED')";

    String FIRST_RESPONSE_BREACHED = "(t.slaFirstResponseDueAt IS NOT NULL AND ("
            + "(t.firstRespondedAt IS NOT NULL AND t.firstRespondedAt > t.slaFirstResponseDueAt)"
            + " OR (t.firstRespondedAt IS NULL AND t.slaFirstResponseDueAt < :now AND " + OPEN + ")))";

    String RESOLUTION_BREACHED = "(t.slaDueAt IS NOT NULL AND ("
            + "(t.resolvedAt IS NOT NULL AND t.resolvedAt > t.slaDueAt)"
            + " OR (t.resolvedAt IS NULL AND t.slaDueAt < :now AND " + OPEN + ")))";

    String BREACHED = "(" + FIRST_RESPONSE_BREACHED + " OR " + RESOLUTION_BREACHED + ")";

    String COHORT_FIGURES = "COUNT(t), SUM(CASE WHEN " + BREACHED + " THEN 1 ELSE 0 END),"
            + " SUM(CASE WHEN t.resolvedAt IS NOT NULL THEN 1 ELSE 0 END)";

    /** {total, first response breached, resolution breached, either} for tickets created since. */
    @Query("SELECT COUNT(t), SUM(CASE WHEN " + FIRST_RESPONSE_BREACHED + " THEN 1 ELSE 0 END),"
            + " SUM(CASE WHEN " + RESOLUTION_BREACHED + " THEN 1 ELSE 0 END),"
            + " SUM(CASE WHEN " + BREACHED + " THEN 1 ELSE 0 END)"
            + " FROM Ticket t WHERE t.createdAt >= :since")
    List<Object[]> breachCounts(@Param("since") Instant since, @Param("now") Instant now);

    /** {created at, first response breached (0/1), resolution breached (0/1)} per breached ticket. */
    @Query("SELECT t.createdAt, CASE WHEN " + FIRST_RESPONSE_BREACHED + " THEN 1 ELSE 0 END,"
            + " CASE WHEN " + RESOLUTION_BREACHED + " THEN 1 ELSE 0 END"
            + " FROM Ticket t WHERE t.createdAt >= :since AND " + BREACHED)
    List<Object[]> breaches(@Param("since") Instant since, @Param("now") Instant now);

    /** {department name, SLA tickets, breached} per department, for tickets with a policy. */
    @Query("SELECT d.name, COUNT(t), SUM(CASE WHEN " + BREACHED + " THEN 1 ELSE 0 END)"
            + " FROM Ticket t LEFT JOIN t.department d WHERE t.createdAt >= :since AND t.slaPolicy IS NOT NULL"
            + " GROUP BY d.id, d.name ORDER BY d.name")
    List<Object[]> breachesByDepartment(@Param("since") Instant since, @Param("now") Instant now);

    /** {priority, SLA tickets, breached} per priority, for tickets with a policy. */
    @Query("SELECT t.priority, COUNT(t), SUM(CASE WHEN " + BREACHED + " THEN 1 ELSE 0 END)"
            + " FROM Ticket t WHERE t.createdAt >= :since AND t.slaPolicy IS NOT NULL"
            + " GROUP BY t.priority ORDER BY t.priority")
    List<Object[]> breachesByPriority(@Param("since") Instant since, @Param("now") Instant now);

    /**
     * {id, reference, subject, first responded at, first response due, resolved at, resolution due}
     * of open tickets with a deadline still to meet between {@code now} and {@code until}.
     */
    @Query("SELECT t.id, t.ticketNumber, t.subject, t.firstRespondedAt, t.slaFirstResponseDueAt, t.resolvedAt,"
            + " t.slaDueAt FROM Ticket t WHERE " + OPEN + " AND ("
            + "(t.firstRespondedAt IS NULL AND t.slaFirstResponseDueAt >= :now AND t.slaFirstResponseDueAt <= :until)"
            + " OR (t.resolvedAt IS NULL AND t.slaDueAt >= :now AND t.slaDueAt <= :until))")
    List<Object[]> atRisk(@Param("now") Instant now, @Param("until") Instant until);

    /**
     * {created at, first responded at, resolved at, priority, channel, department id, department
     * name, agent id, agent name, ticket type} for tickets created since: the hour figures' raw
     * material.
     */
    @Query("SELECT t.createdAt, t.firstRespondedAt, t.resolvedAt, t.priority, t.channel, d.id, d.name, a.id, a.name,"
            + " t.ticketType FROM Ticket t LEFT JOIN t.department d LEFT JOIN t.assignedAgent a WHERE t.createdAt >= :since")
    List<Object[]> timings(@Param("since") Instant since);

    /** {agent id, agent name, tickets, resolved} per assignee of tickets created since. */
    @Query("SELECT a.id, a.name, COUNT(t), SUM(CASE WHEN t.resolvedAt IS NOT NULL THEN 1 ELSE 0 END)"
            + " FROM Ticket t JOIN t.assignedAgent a WHERE t.createdAt >= :since GROUP BY a.id, a.name")
    List<Object[]> agentVolumes(@Param("since") Instant since);

    /** {agent id, average rating} of ratings given since, by the rated ticket's assignee. */
    @Query("SELECT t.assignedAgent.id, AVG(sr.rating) FROM SatisfactionRating sr JOIN sr.ticket t"
            + " WHERE sr.createdAt >= :since AND sr.rating > 0 AND t.assignedAgent IS NOT NULL"
            + " GROUP BY t.assignedAgent.id")
    List<Object[]> agentRatings(@Param("since") Instant since);

    // ── Cohorts: {key, volume, breached, resolved} ──────────────────────

    @Query("SELECT tg.name, " + COHORT_FIGURES + " FROM Ticket t JOIN t.tags tg WHERE t.createdAt >= :since"
            + " GROUP BY tg.id, tg.name ORDER BY tg.name")
    List<Object[]> cohortsByTag(@Param("since") Instant since, @Param("now") Instant now);

    @Query("SELECT d.name, " + COHORT_FIGURES + " FROM Ticket t LEFT JOIN t.department d WHERE t.createdAt >= :since"
            + " GROUP BY d.id, d.name ORDER BY d.name")
    List<Object[]> cohortsByDepartment(@Param("since") Instant since, @Param("now") Instant now);

    @Query("SELECT t.channel, " + COHORT_FIGURES + " FROM Ticket t WHERE t.createdAt >= :since"
            + " GROUP BY t.channel ORDER BY t.channel")
    List<Object[]> cohortsByChannel(@Param("since") Instant since, @Param("now") Instant now);

    @Query("SELECT t.ticketType, " + COHORT_FIGURES + " FROM Ticket t WHERE t.createdAt >= :since"
            + " GROUP BY t.ticketType ORDER BY t.ticketType")
    List<Object[]> cohortsByType(@Param("since") Instant since, @Param("now") Instant now);

    @Query("SELECT t.priority, " + COHORT_FIGURES + " FROM Ticket t WHERE t.createdAt >= :since"
            + " GROUP BY t.priority ORDER BY t.priority")
    List<Object[]> cohortsByPriority(@Param("since") Instant since, @Param("now") Instant now);

    /** {tag name, created at, resolved at} of resolved tickets created since, once per tag. */
    @Query("SELECT tg.name, t.createdAt, t.resolvedAt FROM Ticket t JOIN t.tags tg"
            + " WHERE t.createdAt >= :since AND t.resolvedAt IS NOT NULL")
    List<Object[]> tagResolutions(@Param("since") Instant since);

    /** {key, average rating} of ratings given on tickets created since, per cohort. */
    @Query("SELECT tg.name, AVG(sr.rating) FROM SatisfactionRating sr JOIN sr.ticket t JOIN t.tags tg"
            + " WHERE t.createdAt >= :since AND sr.rating > 0 GROUP BY tg.id, tg.name")
    List<Object[]> ratingsByTag(@Param("since") Instant since);

    @Query("SELECT d.name, AVG(sr.rating) FROM SatisfactionRating sr JOIN sr.ticket t LEFT JOIN t.department d"
            + " WHERE t.createdAt >= :since AND sr.rating > 0 GROUP BY d.id, d.name")
    List<Object[]> ratingsByDepartment(@Param("since") Instant since);

    @Query("SELECT t.channel, AVG(sr.rating) FROM SatisfactionRating sr JOIN sr.ticket t"
            + " WHERE t.createdAt >= :since AND sr.rating > 0 GROUP BY t.channel")
    List<Object[]> ratingsByChannel(@Param("since") Instant since);

    @Query("SELECT t.ticketType, AVG(sr.rating) FROM SatisfactionRating sr JOIN sr.ticket t"
            + " WHERE t.createdAt >= :since AND sr.rating > 0 GROUP BY t.ticketType")
    List<Object[]> ratingsByType(@Param("since") Instant since);

    @Query("SELECT t.priority, AVG(sr.rating) FROM SatisfactionRating sr JOIN sr.ticket t"
            + " WHERE t.createdAt >= :since AND sr.rating > 0 GROUP BY t.priority")
    List<Object[]> ratingsByPriority(@Param("since") Instant since);
}
