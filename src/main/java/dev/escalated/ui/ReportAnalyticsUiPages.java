package dev.escalated.ui;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.repositories.TicketReportRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The analytics report screens' props, as escalated-laravel's {@code Admin\ReportController}
 * builds them for {@code Reports/SlaTrends}, {@code ResponseTimes}, {@code ResolutionTimes},
 * {@code AgentRanking} and {@code Cohorts} (through its {@code ReportScreens} shapes).
 *
 * <p>Figures cover tickets created in the last {@code days} days, dates in UTC. Counts and rates
 * are grouped in the database ({@link TicketReportRepository}); hours are worked out from the
 * timestamps it returns. A breach is what the other report screens count: answered or resolved
 * after the due time, or still open past it.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class ReportAnalyticsUiPages {

    /** Laravel's {@code ReportController::FIRST_RESPONSE_TARGET_HOURS}. */
    static final double FIRST_RESPONSE_TARGET_HOURS = 4;
    /** Laravel's {@code ReportController::RESOLUTION_TARGET_HOURS}. */
    static final double RESOLUTION_TARGET_HOURS = 24;

    private static final Map<String, double[]> FIRST_RESPONSE_BUCKETS = buckets(
            "<1h", 0, 1, "1-4h", 1, 4, "4-8h", 4, 8, "8-24h", 8, 24, ">24h", 24, -1);
    private static final Map<String, double[]> RESOLUTION_BUCKETS = buckets(
            "<4h", 0, 4, "4-8h", 4, 8, "8-24h", 8, 24, "1-3d", 24, 72, "3-7d", 72, 168, ">7d", 168, -1);

    // Columns of TicketReportRepository#timings.
    private static final int CREATED = 0;
    private static final int RESPONDED = 1;
    private static final int RESOLVED = 2;
    private static final int PRIORITY = 3;
    private static final int CHANNEL = 4;
    private static final int DEPARTMENT_NAME = 6;
    private static final int AGENT_ID = 7;
    private static final int AGENT_NAME = 8;
    private static final int TYPE = 9;

    private final TicketReportRepository reports;

    public ReportAnalyticsUiPages(TicketReportRepository reports) {
        this.reports = reports;
    }

    /** {@code Escalated/Admin/Reports/SlaTrends}; {@code groupBy} is {@code day}, {@code week} or {@code month}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> slaTrends(int days, String groupBy) {
        Instant now = Instant.now();
        Instant since = since(now, days);

        Object[] counts = first(reports.breachCounts(since, now));
        long total = number(counts[0]);
        long breached = number(counts[3]);

        Map<String, long[]> byPeriod = new TreeMap<>();
        for (Object[] row : reports.breaches(since, now)) {
            long[] tally = byPeriod.computeIfAbsent(period((Instant) row[0], groupBy), key -> new long[3]);
            tally[0] += number(row[1]);
            tally[1] += number(row[2]);
            tally[2]++;
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        List<Map<String, Object>> byType = new ArrayList<>();
        byPeriod.forEach((period, tally) -> {
            trend.add(point(period, tally[2]));
            Map<String, Object> stacked = new LinkedHashMap<>();
            stacked.put("label", period);
            stacked.put("values", List.of(tally[0], tally[1]));
            byType.add(stacked);
        });

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("period_days", days);
        props.put("breach_trend", trend);
        props.put("breach_by_type_trend", byType);
        props.put("breach_by_department", rates(reports.breachesByDepartment(since, now), "Unassigned"));
        props.put("breach_by_priority", rates(reports.breachesByPriority(since, now), "unknown"));
        props.put("at_risk_tickets", atRisk(now));
        props.put("total_breaches", breached);
        props.put("breach_rate", total > 0 ? round1(breached * 100.0 / total) : 0.0);
        props.put("first_response_breaches", number(counts[1]));
        props.put("resolution_breaches", number(counts[2]));
        return props;
    }

    /** {@code Escalated/Admin/Reports/ResponseTimes}: hours from creation to the first response. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> responseTimes(int days) {
        List<Object[]> rows = reports.timings(since(Instant.now(), days));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("period_days", days);
        Map<String, Double> summary = summary(hours(rows, RESPONDED), FIRST_RESPONSE_TARGET_HOURS);
        props.put("avg_frt", summary.get("avg"));
        props.put("median_frt", summary.get("median"));
        props.put("p90_frt", summary.get("p90"));
        props.put("pct_under_target", summary.get("pct_under_target"));
        props.put("target_hours", (int) FIRST_RESPONSE_TARGET_HOURS);
        props.put("distribution", distribution(hours(rows, RESPONDED), FIRST_RESPONSE_BUCKETS));
        props.put("trend", trend(rows, RESPONDED));
        props.put("by_agent", byAgent(rows, RESPONDED));
        props.put("by_department", averageBy(rows, RESPONDED, row -> label(row[DEPARTMENT_NAME], "Unassigned")));
        props.put("by_priority", averageBy(rows, RESPONDED, row -> label(row[PRIORITY], "unknown")));
        return props;
    }

    /** {@code Escalated/Admin/Reports/ResolutionTimes}: hours from creation to resolution. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> resolutionTimes(int days) {
        List<Object[]> rows = reports.timings(since(Instant.now(), days));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("period_days", days);
        Map<String, Double> summary = summary(hours(rows, RESOLVED), RESOLUTION_TARGET_HOURS);
        props.put("avg_resolution", summary.get("avg"));
        props.put("median_resolution", summary.get("median"));
        props.put("p90_resolution", summary.get("p90"));
        props.put("pct_under_target", summary.get("pct_under_target"));
        props.put("target_hours", (int) RESOLUTION_TARGET_HOURS);
        props.put("distribution", distribution(hours(rows, RESOLVED), RESOLUTION_BUCKETS));
        props.put("trend", trend(rows, RESOLVED));
        props.put("by_agent", byAgent(rows, RESOLVED));
        props.put("by_department", averageBy(rows, RESOLVED, row -> label(row[DEPARTMENT_NAME], "Unassigned")));
        props.put("by_channel", averageBy(rows, RESOLVED, row -> label(row[CHANNEL], "unknown")));
        return props;
    }

    /**
     * {@code Escalated/Admin/Reports/AgentRanking}, scored as Laravel's
     * {@code agentPerformanceRanking}: resolution rate 30%, first response 25% (100 less 5 a
     * hour, 50 without one), CSAT 25% (out of 5) and volume 20% (2 a ticket, at most 100).
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> agentRanking(int days) {
        Instant since = since(Instant.now(), days);
        List<Object[]> rows = reports.timings(since);
        Map<Object, Double> ratings = new HashMap<>();
        for (Object[] row : reports.agentRatings(since)) {
            ratings.put(key(row[0]), ((Number) row[1]).doubleValue());
        }

        List<Map<String, Object>> agents = new ArrayList<>();
        for (Object[] volume : reports.agentVolumes(since)) {
            Object agentId = volume[0];
            List<Object[]> theirs = rows.stream().filter(row -> key(agentId).equals(key(row[AGENT_ID]))).toList();
            long total = number(volume[2]);
            long resolved = number(volume[3]);
            double resolutionRate = total > 0 ? resolved * 100.0 / total : 0;
            double avgResponse = round1(average(hours(theirs, RESPONDED)));
            double avgResolution = round1(average(hours(theirs, RESOLVED)));
            double csat = ratings.getOrDefault(key(agentId), 0.0);

            double frtScore = avgResponse > 0 ? Math.max(0, 100 - avgResponse * 5) : 50;
            double composite = round1(resolutionRate * 0.30 + frtScore * 0.25 + csat * 20 * 0.25
                    + Math.min(100, total * 2) * 0.20);

            Map<String, Object> agent = new LinkedHashMap<>();
            agent.put("agent_id", agentId);
            agent.put("agent_name", volume[1]);
            agent.put("volume", total);
            agent.put("resolution_rate", round1(resolutionRate));
            agent.put("avg_frt", avgResponse);
            agent.put("avg_resolution", avgResolution);
            agent.put("csat", round1(csat));
            agent.put("composite_score", composite);
            agents.add(agent);
        }
        agents.sort(Comparator.comparingDouble((Map<String, Object> agent) -> (double) agent.get("composite_score"))
                .reversed());

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("period_days", days);
        props.put("agents", agents);
        return props;
    }

    /** {@code Escalated/Admin/Reports/Cohorts}: volume, resolution hours, breach rate and CSAT per group. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> cohorts(int days) {
        Instant now = Instant.now();
        Instant since = since(now, days);
        List<Object[]> rows = reports.timings(since);

        Map<String, List<Double>> tagHours = new HashMap<>();
        for (Object[] row : reports.tagResolutions(since)) {
            tagHours.computeIfAbsent(label(row[0], "unknown"), key -> new ArrayList<>())
                    .add(hoursBetween((Instant) row[1], (Instant) row[2]));
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("period_days", days);
        props.put("by_tag", cohorts(reports.cohortsByTag(since, now), reports.ratingsByTag(since), "unknown",
                tagHours));
        props.put("by_department", cohorts(reports.cohortsByDepartment(since, now), reports.ratingsByDepartment(since),
                "Unassigned", resolutionHoursBy(rows, row -> label(row[DEPARTMENT_NAME], "Unassigned"))));
        props.put("by_channel", cohorts(reports.cohortsByChannel(since, now), reports.ratingsByChannel(since),
                "unknown", resolutionHoursBy(rows, row -> label(row[CHANNEL], "unknown"))));
        props.put("by_type", cohorts(reports.cohortsByType(since, now), reports.ratingsByType(since),
                "unspecified", resolutionHoursBy(rows, row -> label(row[TYPE], "unspecified"))));
        props.put("by_priority", cohorts(reports.cohortsByPriority(since, now), reports.ratingsByPriority(since),
                "unspecified", resolutionHoursBy(rows, row -> label(row[PRIORITY], "unspecified"))));
        return props;
    }

    // ── Workings ────────────────────────────────────────────────────────

    private static List<Map<String, Object>> cohorts(List<Object[]> groups, List<Object[]> ratings, String none,
                                                     Map<String, List<Double>> resolutionHours) {
        Map<String, Double> csat = new HashMap<>();
        for (Object[] row : ratings) {
            csat.put(label(row[0], none), ((Number) row[1]).doubleValue());
        }
        List<Map<String, Object>> cohorts = new ArrayList<>();
        for (Object[] row : groups) {
            String name = label(row[0], none);
            long volume = number(row[1]);
            long breached = number(row[2]);
            Map<String, Object> cohort = new LinkedHashMap<>();
            cohort.put("name", name);
            cohort.put("volume", volume);
            cohort.put("avg_resolution", round1(average(resolutionHours.getOrDefault(name, List.of()))));
            cohort.put("breach_rate", volume > 0 ? round1(breached * 100.0 / volume) : 0.0);
            cohort.put("csat", round1(csat.getOrDefault(name, 0.0)));
            cohorts.add(cohort);
        }
        return cohorts;
    }

    private static Map<String, List<Double>> resolutionHoursBy(List<Object[]> rows, Function<Object[], String> key) {
        Map<String, List<Double>> grouped = new HashMap<>();
        for (Object[] row : rows) {
            if (row[RESOLVED] != null) {
                grouped.computeIfAbsent(key.apply(row), k -> new ArrayList<>())
                        .add(hoursBetween((Instant) row[CREATED], (Instant) row[RESOLVED]));
            }
        }
        return grouped;
    }

    /** {@code {label, value}} breach rates (percent) from {key, total, breached} rows. */
    private static List<Map<String, Object>> rates(List<Object[]> rows, String none) {
        List<Map<String, Object>> rates = new ArrayList<>();
        for (Object[] row : rows) {
            long total = number(row[1]);
            long breached = number(row[2]);
            rates.add(point(label(row[0], none), total > 0 ? round1(breached * 100.0 / total) : 0.0));
        }
        return rates;
    }

    /** Open tickets with a deadline due within 8 hours, soonest first, at most 50. */
    private List<Map<String, Object>> atRisk(Instant now) {
        Instant until = now.plus(8, ChronoUnit.HOURS);
        List<Map<String, Object>> tickets = new ArrayList<>();
        for (Object[] row : reports.atRisk(now, until)) {
            Instant due = null;
            if (row[3] == null && within((Instant) row[4], now, until)) {
                due = (Instant) row[4];
            }
            if (row[5] == null && within((Instant) row[6], now, until) && (due == null || ((Instant) row[6]).isBefore(due))) {
                due = (Instant) row[6];
            }
            if (due == null) {
                continue;
            }
            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("id", row[0]);
            ticket.put("reference", row[1]);
            ticket.put("subject", row[2]);
            ticket.put("hours_remaining", round1(Duration.between(now, due).toMinutes() / 60.0));
            tickets.add(ticket);
        }
        tickets.sort(Comparator.comparingDouble(ticket -> (double) ticket.get("hours_remaining")));
        return tickets.size() > 50 ? new ArrayList<>(tickets.subList(0, 50)) : tickets;
    }

    private static boolean within(Instant at, Instant from, Instant until) {
        return at != null && !at.isBefore(from) && !at.isAfter(until);
    }

    /** The agent table: count, average, median and 90th percentile per assignee. */
    private static List<Map<String, Object>> byAgent(List<Object[]> rows, int end) {
        Map<Object, List<Object[]>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[AGENT_ID] != null && row[end] != null) {
                grouped.computeIfAbsent(key(row[AGENT_ID]), id -> new ArrayList<>()).add(row);
            }
        }
        List<Map<String, Object>> agents = new ArrayList<>();
        grouped.forEach((id, theirs) -> {
            List<Double> hours = hours(theirs, end);
            Map<String, Object> agent = new LinkedHashMap<>();
            agent.put("agent_id", theirs.get(0)[AGENT_ID]);
            agent.put("agent_name", theirs.get(0)[AGENT_NAME] == null ? String.valueOf(id) : theirs.get(0)[AGENT_NAME]);
            agent.put("count", hours.size());
            agent.put("avg", round1(average(hours)));
            agent.put("median", round1(percentile(hours, 50)));
            agent.put("p90", round1(percentile(hours, 90)));
            agents.add(agent);
        });
        agents.sort(Comparator.comparing(agent -> String.valueOf(agent.get("agent_name"))));
        return agents;
    }

    /** {@code {label, value}}: the average hours per group. */
    private static List<Map<String, Object>> averageBy(List<Object[]> rows, int end, Function<Object[], String> key) {
        Map<String, List<Double>> grouped = new TreeMap<>();
        for (Object[] row : rows) {
            if (row[end] != null) {
                grouped.computeIfAbsent(key.apply(row), k -> new ArrayList<>())
                        .add(hoursBetween((Instant) row[CREATED], (Instant) row[end]));
            }
        }
        List<Map<String, Object>> points = new ArrayList<>();
        grouped.forEach((label, hours) -> points.add(point(label, round1(average(hours)))));
        return points;
    }

    /** {@code {label, value}}: the average hours per creation date (UTC). */
    private static List<Map<String, Object>> trend(List<Object[]> rows, int end) {
        Map<String, List<Double>> byDate = new TreeMap<>();
        for (Object[] row : rows) {
            if (row[end] != null) {
                byDate.computeIfAbsent(date((Instant) row[CREATED]), d -> new ArrayList<>())
                        .add(hoursBetween((Instant) row[CREATED], (Instant) row[end]));
            }
        }
        List<Map<String, Object>> points = new ArrayList<>();
        byDate.forEach((date, hours) -> points.add(point(date, round1(average(hours)))));
        return points;
    }

    private static List<Map<String, Object>> distribution(List<Double> hours, Map<String, double[]> buckets) {
        List<Map<String, Object>> points = new ArrayList<>();
        buckets.forEach((label, range) -> points.add(point(label, hours.stream()
                .filter(value -> value >= range[0] && (range[1] < 0 || value < range[1])).count())));
        return points;
    }

    private static Map<String, Double> summary(List<Double> hours, double target) {
        Map<String, Double> summary = new LinkedHashMap<>();
        if (hours.isEmpty()) {
            summary.put("avg", 0.0);
            summary.put("median", 0.0);
            summary.put("p90", 0.0);
            summary.put("pct_under_target", 0.0);
            return summary;
        }
        summary.put("avg", round1(average(hours)));
        summary.put("median", round1(percentile(hours, 50)));
        summary.put("p90", round1(percentile(hours, 90)));
        summary.put("pct_under_target",
                round1(hours.stream().filter(value -> value <= target).count() * 100.0 / hours.size()));
        return summary;
    }

    /** The hours from creation to {@code end} of each row that has one. */
    private static List<Double> hours(List<Object[]> rows, int end) {
        List<Double> hours = new ArrayList<>();
        for (Object[] row : rows) {
            if (row[end] != null && row[CREATED] != null) {
                hours.add(hoursBetween((Instant) row[CREATED], (Instant) row[end]));
            }
        }
        return hours;
    }

    private static double hoursBetween(Instant from, Instant to) {
        return Duration.between(from, to).toMillis() / 3_600_000.0;
    }

    private static double average(List<Double> values) {
        return values.isEmpty() ? 0.0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /** Laravel's {@code ReportingService::percentile}: linear between the closest ranks. */
    static double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        double index = percentile / 100 * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        return sorted.get(lower) + (index - lower) * (sorted.get(upper) - sorted.get(lower));
    }

    /** A breach's bucket: its UTC creation date, ISO week ({@code 2026-W43}) or month ({@code 2026-10}). */
    static String period(Instant createdAt, String groupBy) {
        LocalDate date = createdAt.atOffset(ZoneOffset.UTC).toLocalDate();
        if ("week".equals(groupBy)) {
            return String.format(Locale.ROOT, "%d-W%02d",
                    date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        }
        if ("month".equals(groupBy)) {
            return String.format(Locale.ROOT, "%d-%02d", date.getYear(), date.getMonthValue());
        }
        return date.toString();
    }

    private static String label(Object value, String none) {
        if (value == null || String.valueOf(value).isEmpty()) {
            return none;
        }
        return value instanceof Enum<?> constant ? constant.name().toLowerCase(Locale.ROOT) : String.valueOf(value);
    }

    private static Object key(Object id) {
        return id instanceof Number number ? (Object) number.longValue() : id;
    }

    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static Object[] first(List<Object[]> rows) {
        return rows.isEmpty() ? new Object[] {0L, 0L, 0L, 0L} : rows.get(0);
    }

    private static Map<String, Object> point(String label, Object value) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("label", label);
        point.put("value", value);
        return point;
    }

    private static String date(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalDate().toString();
    }

    private static Instant since(Instant now, int days) {
        return now.minus(days, ChronoUnit.DAYS);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static Map<String, double[]> buckets(Object... spec) {
        Map<String, double[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < spec.length; i += 3) {
            buckets.put((String) spec[i], new double[] {((Number) spec[i + 1]).doubleValue(),
                    ((Number) spec[i + 2]).doubleValue()});
        }
        return buckets;
    }
}
