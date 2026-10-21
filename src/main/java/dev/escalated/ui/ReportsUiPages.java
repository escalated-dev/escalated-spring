package dev.escalated.ui;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.SatisfactionRating;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.SatisfactionRatingRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The report screens' props, as escalated-laravel's {@code Admin\ReportController} and
 * {@code ReportingService} build them for {@code Escalated/Admin/Reports} and
 * {@code Reports/Dashboard}, {@code AgentMetrics}, {@code SlaReport}, {@code CsatReport} and
 * {@code Comparison}.
 *
 * <p>Figures cover tickets created in the last {@code days} days (dates in UTC), except the
 * dashboard's status and priority breakdowns, which are all-time as on Laravel. They are worked
 * out from the period's tickets and submitted ratings (a survey sent but not answered is not a
 * rating). A ticket breached its SLA when it was answered or resolved after the due time, or is
 * still open past it.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class ReportsUiPages {

    /** The period a screen asks for: {@code period}, else {@code days}, else 30; 1 to 365. */
    public static int days(Map<String, String> query) {
        Long given = UiTicketFilters.parseLong(query.get("period"));
        if (given == null) {
            given = UiTicketFilters.parseLong(query.get("days"));
        }
        return given == null ? 30 : (int) Math.max(1, Math.min(365, given));
    }

    private final TicketRepository tickets;
    private final SatisfactionRatingRepository ratings;
    private final SlaPolicyRepository policies;

    public ReportsUiPages(TicketRepository tickets, SatisfactionRatingRepository ratings, SlaPolicyRepository policies) {
        this.tickets = tickets;
        this.ratings = ratings;
        this.policies = policies;
    }

    /** {@code Escalated/Admin/Reports}: the overview the Reports link opens. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> overview(int days) {
        Instant since = since(days);
        List<Ticket> period = createdSince(since);
        List<SatisfactionRating> rated = ratings.findSubmittedSince(since);

        Map<String, Object> csat = new LinkedHashMap<>();
        csat.put("average", averageRating(rated));
        csat.put("total", rated.size());
        Map<String, Long> breakdown = new TreeMap<>();
        rated.forEach(rating -> breakdown.merge(String.valueOf(rating.getRating()), 1L, Long::sum));
        csat.put("breakdown", breakdown);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("period_days", days);
        props.put("total_tickets", period.size());
        props.put("resolved_tickets", tickets.countResolvedSince(since));
        props.put("avg_first_response_hours", averageHours(period, Ticket::getFirstRespondedAt));
        props.put("sla_breach_count", period.stream().filter(ReportsUiPages::breached).count());
        props.put("by_status", counts(period, ticket -> UiPresenter.status(ticket.getStatus())));
        props.put("by_priority", counts(period, ticket -> UiPresenter.priority(ticket.getPriority())));
        props.put("csat", csat);
        return props;
    }

    /** {@code Escalated/Admin/Reports/Dashboard}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> dashboard(int days) {
        Instant since = since(days);
        List<Ticket> period = createdSince(since);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("period_days", days);
        props.put("volume", series(counts(period, ticket -> date(ticket.getCreatedAt()))));
        props.put("by_status", series(grouped(tickets.countByStatusSince(Instant.EPOCH))));
        props.put("by_priority", series(grouped(tickets.countByPrioritySince(Instant.EPOCH))));
        props.put("avg_response_hours", averageHours(period, Ticket::getFirstRespondedAt));
        props.put("avg_resolution_hours", averageHours(period, Ticket::getResolvedAt));
        props.put("sla_compliance", compliance(period));
        props.put("csat_average", averageRating(ratings.findSubmittedSince(since)));
        props.put("agent_performance", agentPerformance(period));
        return props;
    }

    /** {@code Escalated/Admin/Reports/AgentMetrics}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> agents(int days) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("period_days", days);
        props.put("agents", agentPerformance(createdSince(since(days))));
        return props;
    }

    /** {@code Escalated/Admin/Reports/SlaReport}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> sla(int days) {
        List<Ticket> period = createdSince(since(days));

        List<Map<String, Object>> byPolicy = new ArrayList<>();
        List<SlaPolicy> active = new ArrayList<>(policies.findAll());
        active.removeIf(policy -> !policy.isActive());
        active.sort(Comparator.comparing(SlaPolicy::getId));
        for (SlaPolicy policy : active) {
            List<Ticket> covered = period.stream()
                    .filter(ticket -> ticket.getSlaPolicy() != null && policy.getId().equals(ticket.getSlaPolicy().getId()))
                    .toList();
            long breached = covered.stream().filter(ReportsUiPages::breached).count();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("policy_name", policy.getName());
            row.put("total", covered.size());
            row.put("met", covered.size() - breached);
            row.put("breached", breached);
            byPolicy.add(row);
        }

        List<Map<String, Object>> breaches = new ArrayList<>();
        period.stream().filter(ReportsUiPages::breached)
                .sorted(Comparator.comparing(Ticket::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Ticket::getId, Comparator.reverseOrder()))
                .limit(100)
                .forEach(ticket -> breaches.add(breach(ticket)));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("period_days", days);
        props.put("compliance_rate", compliance(period));
        props.put("compliance_by_policy", byPolicy);
        props.put("breaches", breaches);
        return props;
    }

    /** {@code Escalated/Admin/Reports/CsatReport}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> csat(int days) {
        Instant since = since(days);
        List<SatisfactionRating> rated = ratings.findSubmittedSince(since);
        long ticketCount = createdSince(since).size();

        Map<Long, List<SatisfactionRating>> byAgent = new TreeMap<>();
        Map<Long, AgentProfile> agents = new LinkedHashMap<>();
        for (SatisfactionRating rating : rated) {
            AgentProfile agent = rating.getTicket() == null ? null : rating.getTicket().getAssignedAgent();
            if (agent != null) {
                byAgent.computeIfAbsent(agent.getId(), id -> new ArrayList<>()).add(rating);
                agents.put(agent.getId(), agent);
            }
        }
        List<Map<String, Object>> agentRows = new ArrayList<>();
        byAgent.forEach((agentId, list) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("agent_id", agentId);
            row.put("agent_name", agents.get(agentId).getName());
            row.put("avg_rating", averageRating(list));
            row.put("total_ratings", list.size());
            agentRows.add(row);
        });

        Map<String, List<SatisfactionRating>> byDate = new TreeMap<>();
        rated.forEach(rating -> byDate.computeIfAbsent(date(rating.getCreatedAt()), d -> new ArrayList<>()).add(rating));
        List<Map<String, Object>> overTime = new ArrayList<>();
        byDate.forEach((date, list) -> overTime.add(point(date, averageRating(list))));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("period_days", days);
        props.put("csat_average", averageRating(rated));
        props.put("response_rate", ticketCount > 0 ? round1(rated.size() * 100.0 / ticketCount) : 0.0);
        props.put("total_ratings", rated.size());
        props.put("by_agent", agentRows);
        props.put("over_time", overTime);
        return props;
    }

    /**
     * {@code Escalated/Admin/Reports/Comparison}: the last {@code days} days beside the {@code days}
     * before them, each a full set of figures, as Laravel pivots {@code periodComparison}.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> comparison(int days) {
        Instant now = Instant.now();
        Instant currentStart = now.minus(days, ChronoUnit.DAYS);
        Instant previousStart = now.minus(2L * days, ChronoUnit.DAYS);
        List<SatisfactionRating> rated = ratings.findSubmittedSince(previousStart);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("period_days", days);
        props.put("current", period(createdBetween(currentStart, now), within(rated, currentStart, now), currentStart, now));
        props.put("previous", period(createdBetween(previousStart, currentStart), within(rated, previousStart, currentStart),
                previousStart, currentStart));
        return props;
    }

    // ── Workings ────────────────────────────────────────────────────────

    private Map<String, Object> period(List<Ticket> created, List<SatisfactionRating> rated, Instant start, Instant end) {
        Map<String, Object> side = new LinkedHashMap<>();
        side.put("total_tickets", created.size());
        side.put("resolved_tickets", resolvedBetween(start, end));
        side.put("avg_frt", averageHours(created, Ticket::getFirstRespondedAt));
        side.put("avg_resolution", averageHours(created, Ticket::getResolvedAt));
        side.put("sla_compliance", compliance(created));
        side.put("csat", averageRating(rated));
        side.put("breach_count", created.stream().filter(ReportsUiPages::breached).count());
        side.put("volume_trend", series(counts(created, ticket -> date(ticket.getCreatedAt()))));
        return side;
    }

    private long resolvedBetween(Instant start, Instant end) {
        Specification<Ticket> resolved = (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("resolvedAt"), start), cb.lessThan(root.get("resolvedAt"), end));
        return tickets.count(resolved);
    }

    private static List<SatisfactionRating> within(List<SatisfactionRating> rated, Instant start, Instant end) {
        return rated.stream().filter(rating -> rating.getCreatedAt() != null && !rating.getCreatedAt().isBefore(start)
                && rating.getCreatedAt().isBefore(end)).toList();
    }

    private List<Ticket> createdBetween(Instant start, Instant end) {
        Specification<Ticket> created = (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("createdAt"), start), cb.lessThan(root.get("createdAt"), end));
        return tickets.findAll(created);
    }

    /** Per assignee, as {@code ReportingService::getAgentPerformance}. */
    private static List<Map<String, Object>> agentPerformance(List<Ticket> period) {
        Map<Long, List<Ticket>> byAgent = new TreeMap<>();
        Map<Long, AgentProfile> agents = new LinkedHashMap<>();
        for (Ticket ticket : period) {
            AgentProfile agent = ticket.getAssignedAgent();
            if (agent != null) {
                byAgent.computeIfAbsent(agent.getId(), id -> new ArrayList<>()).add(ticket);
                agents.put(agent.getId(), agent);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        byAgent.forEach((agentId, list) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("agent_id", agentId);
            row.put("agent_name", agents.get(agentId).getName());
            row.put("total_tickets", list.size());
            row.put("resolved_tickets", list.stream().filter(ticket -> ticket.getResolvedAt() != null).count());
            row.put("avg_response_hours", averageHours(list, Ticket::getFirstRespondedAt));
            row.put("avg_resolution_hours", averageHours(list, Ticket::getResolvedAt));
            rows.add(row);
        });
        return rows;
    }

    private static Map<String, Object> breach(Ticket ticket) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", ticket.getId());
        row.put("reference", ticket.getTicketNumber());
        row.put("subject", ticket.getSubject());
        row.put("assigned_to", ticket.getAssignedAgent() == null ? null : ticket.getAssignedAgent().getId());
        row.put("assignee", UiPresenter.agentName(ticket.getAssignedAgent()));
        row.put("sla_policy_id", ticket.getSlaPolicy() == null ? null : ticket.getSlaPolicy().getId());
        Map<String, Object> policy = null;
        if (ticket.getSlaPolicy() != null) {
            policy = new LinkedHashMap<>();
            policy.put("id", ticket.getSlaPolicy().getId());
            policy.put("name", ticket.getSlaPolicy().getName());
        }
        row.put("sla_policy", policy);
        row.put("sla_first_response_breached", UiPresenter.firstResponseBreached(ticket));
        row.put("sla_resolution_breached", UiPresenter.resolutionBreached(ticket));
        row.put("first_response_at", UiPresenter.iso(ticket.getFirstRespondedAt()));
        row.put("resolved_at", UiPresenter.iso(ticket.getResolvedAt()));
        row.put("created_at", UiPresenter.iso(ticket.getCreatedAt()));
        return row;
    }

    /** The share of the period's SLA tickets with no breach, as a percentage; 100 with none. */
    private static double compliance(List<Ticket> period) {
        List<Ticket> covered = period.stream().filter(ticket -> ticket.getSlaPolicy() != null).toList();
        if (covered.isEmpty()) {
            return 100.0;
        }
        long breached = covered.stream().filter(ReportsUiPages::breached).count();
        return round1((covered.size() - breached) * 100.0 / covered.size());
    }

    private static boolean breached(Ticket ticket) {
        return UiPresenter.firstResponseBreached(ticket) || UiPresenter.resolutionBreached(ticket);
    }

    private List<Ticket> createdSince(Instant since) {
        Specification<Ticket> created = (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), since);
        return tickets.findAll(created);
    }

    private static double averageHours(List<Ticket> list, Function<Ticket, Instant> end) {
        double total = 0;
        int counted = 0;
        for (Ticket ticket : list) {
            Instant finished = end.apply(ticket);
            if (finished != null && ticket.getCreatedAt() != null) {
                total += Duration.between(ticket.getCreatedAt(), finished).toMillis() / 3_600_000.0;
                counted++;
            }
        }
        return counted == 0 ? 0.0 : round1(total / counted);
    }

    private static double averageRating(List<SatisfactionRating> list) {
        return list.isEmpty() ? 0.0 : round1(list.stream().mapToInt(SatisfactionRating::getRating).average().orElse(0));
    }

    private static Map<String, Long> counts(List<Ticket> list, Function<Ticket, String> key) {
        Map<String, Long> counts = new TreeMap<>();
        for (Ticket ticket : list) {
            String value = key.apply(ticket);
            if (value != null) {
                counts.merge(value, 1L, Long::sum);
            }
        }
        return counts;
    }

    /** {@code [enum, count]} rows as lower-cased label to count. */
    private static Map<String, Long> grouped(List<Object[]> rows) {
        Map<String, Long> counts = new TreeMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                counts.merge(row[0] instanceof Enum<?> constant ? constant.name().toLowerCase(java.util.Locale.ROOT)
                        : String.valueOf(row[0]), ((Number) row[1]).longValue(), Long::sum);
            }
        }
        return counts;
    }

    private static List<Map<String, Object>> series(Map<String, Long> counts) {
        List<Map<String, Object>> points = new ArrayList<>();
        counts.forEach((label, value) -> points.add(point(label, value)));
        return points;
    }

    private static Map<String, Object> point(String label, Object value) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("label", label);
        point.put("value", value);
        return point;
    }

    private static String date(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC).toLocalDate().toString();
    }

    private static Instant since(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
