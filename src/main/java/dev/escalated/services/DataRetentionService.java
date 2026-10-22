package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.TicketStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the data retention settings the admin Data retention page saves
 * ({@code retention_closed_tickets}, {@code retention_attachments}, {@code retention_audit_logs}),
 * as escalated-laravel's {@code escalated:purge-expired}. Every period defaults to {@code never},
 * which purges nothing.
 *
 * <p>Tickets have no soft delete in this port, so a closed ticket past its period is deleted
 * outright, together with every row that points at it; Laravel's 30-day grace period and
 * {@code --force} do not apply. Attachment rows past their period are deleted; their files stay
 * where the {@code AttachmentStorage} put them (it has no delete). Expired guest verification
 * codes are deleted. {@link dev.escalated.scheduling.RetentionScheduler} runs it daily.
 */
@Service
public class DataRetentionService {

    /** What one run deleted, or on a dry run would delete. */
    public record Report(boolean dryRun, int closedTickets, int attachments, int auditLogs, int guestVerifications) {
    }

    /** What the periods would purge now: the page's {@code purgePreview}. */
    public record Preview(long tickets, long attachments, long auditLogs) {
    }

    private static final Map<String, Integer> DAYS = Map.of(
            "90_days", 90,
            "180_days", 180,
            "365_days", 365,
            "1_year", 365,
            "2_years", 730,
            "3_years", 1095,
            "5_years", 1825);

    private static final int BATCH = 200;

    private static final String EXPIRED_TICKETS =
            " FROM escalated_tickets WHERE status = :closed AND closed_at IS NOT NULL AND closed_at < :cutoff";

    /** Rows that point at a ticket, deleted before it, children first ({@code :ids} the batch). */
    private static final List<String> DEPENDENTS = List.of(
            "DELETE FROM escalated_attachments WHERE ticket_id IN (:ids)"
                    + " OR reply_id IN (SELECT id FROM escalated_replies WHERE ticket_id IN (:ids))",
            "DELETE FROM escalated_side_conversation_replies WHERE side_conversation_id IN"
                    + " (SELECT id FROM escalated_side_conversations WHERE ticket_id IN (:ids))",
            "DELETE FROM escalated_side_conversations WHERE ticket_id IN (:ids)",
            "DELETE FROM escalated_chat_sessions WHERE ticket_id IN (:ids)",
            "DELETE FROM escalated_custom_field_values WHERE ticket_id IN (:ids)",
            "DELETE FROM escalated_deferred_workflow_jobs WHERE ticket_id IN (:ids)",
            "DELETE FROM escalated_rule_executions WHERE ticket_id IN (:ids)",
            "DELETE FROM escalated_satisfaction_ratings WHERE ticket_id IN (:ids)",
            "DELETE FROM escalated_ticket_activities WHERE ticket_id IN (:ids)",
            "DELETE FROM escalated_ticket_followers WHERE ticket_id IN (:ids)",
            "DELETE FROM escalated_ticket_subjects WHERE ticket_id IN (:ids)",
            "DELETE FROM escalated_workflow_logs WHERE ticket_id IN (:ids)",
            "DELETE FROM escalated_ticket_tags WHERE ticket_id IN (:ids)",
            "DELETE FROM escalated_ticket_links WHERE source_ticket_id IN (:ids) OR target_ticket_id IN (:ids)",
            "DELETE FROM escalated_replies WHERE ticket_id IN (:ids)",
            "UPDATE escalated_tickets SET merged_into_ticket_id = NULL WHERE merged_into_ticket_id IN (:ids)",
            "DELETE FROM escalated_tickets WHERE id IN (:ids)");

    private final SettingsService settings;
    private final EntityManager entityManager;

    public DataRetentionService(
            SettingsService settings,
            @Qualifier("escalatedEntityManagerFactory") EntityManagerFactory factory) {
        this.settings = settings;
        this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(factory);
    }

    /** The period in days for a setting value; empty for {@code never} or anything unknown. */
    public static Optional<Integer> days(String setting) {
        return Optional.ofNullable(setting == null ? null : DAYS.get(setting));
    }

    private Optional<Instant> cutoff(String key, Instant now) {
        return days(settings.getOrDefault(key, "never")).map(d -> now.minus(Duration.ofDays(d)));
    }

    /** Never throws: a count it cannot make is 0, as Laravel's page does. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Preview preview(Instant now) {
        long tickets = 0;
        long attachments = 0;
        long auditLogs = 0;
        try {
            Optional<Instant> ticketCutoff = cutoff("retention_closed_tickets", now);
            if (ticketCutoff.isPresent()) {
                tickets = countExpiredTickets(ticketCutoff.get());
            }
            Optional<Instant> attachmentCutoff = cutoff("retention_attachments", now);
            if (attachmentCutoff.isPresent()) {
                attachments = count("SELECT COUNT(*) FROM escalated_attachments WHERE created_at < :cutoff",
                        attachmentCutoff.get());
            }
            Optional<Instant> auditCutoff = cutoff("retention_audit_logs", now);
            if (auditCutoff.isPresent()) {
                auditLogs = count("SELECT COUNT(*) FROM escalated_audit_logs WHERE created_at < :cutoff",
                        auditCutoff.get());
            }
        } catch (RuntimeException e) {
            return new Preview(0, 0, 0);
        }
        return new Preview(tickets, attachments, auditLogs);
    }

    /** Runs the purge as of {@code now}. With {@code dryRun} it only counts. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Report purgeExpired(boolean dryRun, Instant now) {
        int closed = 0;
        Optional<Instant> ticketCutoff = cutoff("retention_closed_tickets", now);
        if (ticketCutoff.isPresent()) {
            closed = (int) countExpiredTickets(ticketCutoff.get());
            if (!dryRun && closed > 0) {
                deleteExpiredTickets(ticketCutoff.get());
            }
        }

        int attachments = purgeBefore("retention_attachments", "escalated_attachments", now, dryRun);
        int auditLogs = purgeBefore("retention_audit_logs", "escalated_audit_logs", now, dryRun);

        int guestCodes = (int) count("SELECT COUNT(*) FROM escalated_guest_verifications WHERE expires_at <= :cutoff", now);
        if (!dryRun && guestCodes > 0) {
            entityManager.createNativeQuery("DELETE FROM escalated_guest_verifications WHERE expires_at <= :cutoff")
                    .setParameter("cutoff", now)
                    .executeUpdate();
        }

        entityManager.clear();
        return new Report(dryRun, closed, attachments, auditLogs, guestCodes);
    }

    private long countExpiredTickets(Instant cutoff) {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*)" + EXPIRED_TICKETS)
                .setParameter("closed", TicketStatus.CLOSED.name())
                .setParameter("cutoff", cutoff)
                .getSingleResult()).longValue();
    }

    private long count(String sql, Instant cutoff) {
        return ((Number) entityManager.createNativeQuery(sql).setParameter("cutoff", cutoff).getSingleResult())
                .longValue();
    }

    private int purgeBefore(String key, String table, Instant now, boolean dryRun) {
        Optional<Instant> cutoff = cutoff(key, now);
        if (cutoff.isEmpty()) {
            return 0;
        }
        int count = (int) count("SELECT COUNT(*) FROM " + table + " WHERE created_at < :cutoff", cutoff.get());
        if (!dryRun && count > 0) {
            entityManager.createNativeQuery("DELETE FROM " + table + " WHERE created_at < :cutoff")
                    .setParameter("cutoff", cutoff.get())
                    .executeUpdate();
        }
        return count;
    }

    private void deleteExpiredTickets(Instant cutoff) {
        entityManager.flush();
        while (true) {
            Query select = entityManager.createNativeQuery("SELECT id" + EXPIRED_TICKETS + " ORDER BY id")
                    .setParameter("closed", TicketStatus.CLOSED.name())
                    .setParameter("cutoff", cutoff)
                    .setMaxResults(BATCH);
            List<Long> ids = new ArrayList<>();
            for (Object id : (List<?>) select.getResultList()) {
                ids.add(((Number) id).longValue());
            }
            if (ids.isEmpty()) {
                return;
            }
            for (String statement : DEPENDENTS) {
                entityManager.createNativeQuery(statement).setParameter("ids", ids).executeUpdate();
            }
        }
    }
}
