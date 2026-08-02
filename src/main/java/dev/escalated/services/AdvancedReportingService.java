package dev.escalated.services;

import dev.escalated.dto.reporting.AgentPerformanceReport;
import dev.escalated.dto.reporting.CsatReport;
import dev.escalated.dto.reporting.OverviewReport;
import dev.escalated.dto.reporting.PeriodComparisonReport;
import dev.escalated.dto.reporting.ReportSeriesPoint;
import dev.escalated.dto.reporting.ResponseTimeReport;
import dev.escalated.dto.reporting.SlaReport;
import dev.escalated.dto.reporting.VolumeReport;
import dev.escalated.repositories.SatisfactionRatingRepository;
import dev.escalated.repositories.TicketRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/**
 * Advanced reporting: repository aggregation queries fed through pure math
 * helpers (percentiles, composite scores, date series, period changes) to
 * produce the JSON report DTOs exposed by {@code AdminReportController}.
 *
 * <p>The {@code static} helpers below are pure functions covered by unit
 * tests; the instance methods orchestrate the repositories and reuse them.
 */
@Service
public class AdvancedReportingService {

    private static final ZoneOffset ZONE = ZoneOffset.UTC;

    private final TicketRepository ticketRepository;
    private final SatisfactionRatingRepository satisfactionRatingRepository;

    public AdvancedReportingService(TicketRepository ticketRepository,
                                    SatisfactionRatingRepository satisfactionRatingRepository) {
        this.ticketRepository = ticketRepository;
        this.satisfactionRatingRepository = satisfactionRatingRepository;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Reports
    // ──────────────────────────────────────────────────────────────────────

    /** Headline dashboard metrics for the trailing {@code days}-day period. */
    public OverviewReport overview(int days) {
        Instant since = since(days);
        Instant now = Instant.now();

        long total = ticketRepository.countCreatedSince(since);
        long resolved = ticketRepository.countResolvedSince(since);
        double resolutionRate = total > 0 ? round1((double) resolved / total * 100) : 0.0;

        double avgFrt = averageHours(ticketRepository.firstResponseTimings(since));
        double avgResolution = averageHours(ticketRepository.resolutionTimings(since));

        Double csat = satisfactionRatingRepository.avgRatingSince(since);
        double csatAverage = csat == null ? 0.0 : round1(csat);

        return new OverviewReport(
                days,
                total,
                resolved,
                resolutionRate,
                round1(avgFrt),
                round1(avgResolution),
                slaComplianceRate(since, now),
                csatAverage,
                toSeries(ticketRepository.countByStatusSince(since)),
                toSeries(ticketRepository.countByPrioritySince(since)),
                toSeries(ticketRepository.countByChannelSince(since)));
    }

    /** First-response time distribution, percentiles, and daily trend. */
    public ResponseTimeReport firstResponseTime(int days) {
        return responseTime(days, ticketRepository.firstResponseTimings(since(days)), frtBuckets());
    }

    /** Resolution time distribution, percentiles, and daily trend. */
    public ResponseTimeReport resolutionTime(int days) {
        return responseTime(days, ticketRepository.resolutionTimings(since(days)), resolutionBuckets());
    }

    /** SLA compliance and breach trends, with a per-priority breakdown. */
    public SlaReport slaReport(int days) {
        Instant since = since(days);
        Instant now = Instant.now();

        long total = ticketRepository.countWithSlaSince(since);
        long frtBreaches = ticketRepository.countFirstResponseBreaches(since, now);
        long resolutionBreaches = ticketRepository.countResolutionBreaches(since, now);
        long anyBreach = ticketRepository.countSlaBreaches(since, now);
        double compliance = total > 0 ? round1((double) (total - anyBreach) / total * 100) : 100.0;

        Map<String, Long> breachByPriority = new HashMap<>();
        for (Object[] row : ticketRepository.countSlaBreachesByPriority(since, now)) {
            breachByPriority.put(label(row[0]), asLong(row[1]));
        }

        List<SlaReport.PriorityBreakdown> byPriority = new ArrayList<>();
        for (Object[] row : ticketRepository.countSlaTicketsByPriority(since)) {
            String priority = label(row[0]);
            long priorityTotal = asLong(row[1]);
            long breached = breachByPriority.getOrDefault(priority, 0L);
            double rate = priorityTotal > 0 ? round1((double) breached / priorityTotal * 100) : 0.0;
            byPriority.add(new SlaReport.PriorityBreakdown(priority, priorityTotal, breached, rate));
        }

        return new SlaReport(days, compliance, total, frtBreaches, resolutionBreaches, anyBreach, byPriority);
    }

    /** CSAT average, response rate, rating breakdown, and daily trend. */
    public CsatReport csat(int days) {
        Instant since = since(days);

        Double avg = satisfactionRatingRepository.avgRatingSince(since);
        double average = avg == null ? 0.0 : round1(avg);
        long totalRatings = satisfactionRatingRepository.countRatingsSince(since);
        long totalTickets = ticketRepository.countCreatedSince(since);
        double responseRate = totalTickets > 0 ? round1((double) totalRatings / totalTickets * 100) : 0.0;

        List<ReportSeriesPoint> breakdown = toSeries(satisfactionRatingRepository.countByRatingSince(since));
        List<CsatReport.TrendPoint> overTime = csatOverTime(satisfactionRatingRepository.ratingRowsSince(since));

        return new CsatReport(days, average, totalRatings, responseRate, breakdown, overTime);
    }

    /** Daily ticket volume (zero-filled) plus priority and channel cohorts. */
    public VolumeReport volume(int days) {
        Instant since = since(days);
        long total = ticketRepository.countCreatedSince(since);

        Map<LocalDate, Long> counts = new HashMap<>();
        for (Instant createdAt : ticketRepository.createdAtSince(since)) {
            counts.merge(createdAt.atZone(ZONE).toLocalDate(), 1L, Long::sum);
        }

        LocalDate to = LocalDate.now(ZONE);
        LocalDate from = to.minusDays(days);
        List<ReportSeriesPoint> series = new ArrayList<>();
        for (LocalDate date : dateSeries(from, to)) {
            series.add(new ReportSeriesPoint(date.toString(), counts.getOrDefault(date, 0L)));
        }

        return new VolumeReport(
                days,
                total,
                series,
                toSeries(ticketRepository.countByPrioritySince(since)),
                toSeries(ticketRepository.countByChannelSince(since)));
    }

    /** Ranked agent performance using {@link #compositeScore}. */
    public AgentPerformanceReport agentPerformance(int days) {
        Instant since = since(days);

        Map<Long, List<Double>> frtByAgent = new HashMap<>();
        for (Object[] row : ticketRepository.agentFirstResponseTimings(since)) {
            long agentId = asLong(row[0]);
            frtByAgent.computeIfAbsent(agentId, key -> new ArrayList<>())
                    .add(hoursBetween((Instant) row[1], (Instant) row[2]));
        }

        Map<Long, Double> csatByAgent = new HashMap<>();
        for (Object[] row : satisfactionRatingRepository.avgRatingByAgentSince(since)) {
            csatByAgent.put(asLong(row[0]), asDouble(row[1]));
        }

        List<AgentPerformanceReport.AgentRow> rows = new ArrayList<>();
        for (Object[] row : ticketRepository.agentTicketCounts(since)) {
            long agentId = asLong(row[0]);
            String name = (String) row[1];
            long total = asLong(row[2]);
            long resolved = asLong(row[3]);
            double resolutionRate = total > 0 ? (double) resolved / total * 100 : 0.0;

            List<Double> frtValues = frtByAgent.getOrDefault(agentId, Collections.emptyList());
            Double avgFrt = frtValues.isEmpty()
                    ? null
                    : frtValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            Double avgCsat = csatByAgent.get(agentId);

            double composite = compositeScore(resolutionRate, avgFrt, null, avgCsat);
            rows.add(new AgentPerformanceReport.AgentRow(
                    agentId,
                    name,
                    total,
                    resolved,
                    round1(resolutionRate),
                    avgFrt == null ? 0.0 : round1(avgFrt),
                    avgCsat == null ? 0.0 : round1(avgCsat),
                    composite,
                    0));
        }

        rows.sort((a, b) -> Double.compare(b.compositeScore(), a.compositeScore()));

        List<AgentPerformanceReport.AgentRow> ranked = new ArrayList<>();
        int rank = 1;
        for (AgentPerformanceReport.AgentRow row : rows) {
            ranked.add(new AgentPerformanceReport.AgentRow(
                    row.agentId(),
                    row.agentName(),
                    row.totalTickets(),
                    row.resolvedTickets(),
                    row.resolutionRate(),
                    row.avgResponseHours(),
                    row.csatAverage(),
                    row.compositeScore(),
                    rank++));
        }

        return new AgentPerformanceReport(days, ranked);
    }

    /** Current period vs. the preceding equal-length period, with % changes. */
    public PeriodComparisonReport periodComparison(int days) {
        Instant now = Instant.now();
        Instant currentStart = now.minus(days, ChronoUnit.DAYS);
        Instant previousStart = now.minus(2L * days, ChronoUnit.DAYS);

        Map<String, Double> current = periodMetrics(currentStart, now);
        Map<String, Double> previous = periodMetrics(previousStart, currentStart);
        Map<String, Double> changes = calculateChanges(current, previous);

        return new PeriodComparisonReport(days, current, previous, changes);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private orchestration helpers
    // ──────────────────────────────────────────────────────────────────────

    private ResponseTimeReport responseTime(int days, List<Object[]> rows, Map<String, double[]> buckets) {
        List<Double> hours = new ArrayList<>();
        for (Object[] row : rows) {
            hours.add(hoursBetween((Instant) row[0], (Instant) row[1]));
        }

        double avg = hours.isEmpty()
                ? 0.0
                : round1(hours.stream().mapToDouble(Double::doubleValue).average().orElse(0));

        return new ResponseTimeReport(
                days,
                hours.size(),
                avg,
                buildHistogram(hours, buckets),
                calculatePercentiles(hours),
                responseTimeTrend(rows));
    }

    private List<ResponseTimeReport.TrendPoint> responseTimeTrend(List<Object[]> rows) {
        Map<LocalDate, List<Double>> byDate = new TreeMap<>();
        for (Object[] row : rows) {
            Instant createdAt = (Instant) row[0];
            byDate.computeIfAbsent(createdAt.atZone(ZONE).toLocalDate(), key -> new ArrayList<>())
                    .add(hoursBetween(createdAt, (Instant) row[1]));
        }

        List<ResponseTimeReport.TrendPoint> trend = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Double>> entry : byDate.entrySet()) {
            List<Double> values = entry.getValue();
            double avg = round1(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            trend.add(new ResponseTimeReport.TrendPoint(entry.getKey().toString(), avg, values.size()));
        }
        return trend;
    }

    private List<CsatReport.TrendPoint> csatOverTime(List<Object[]> rows) {
        Map<LocalDate, List<Integer>> byDate = new TreeMap<>();
        for (Object[] row : rows) {
            Instant createdAt = (Instant) row[0];
            byDate.computeIfAbsent(createdAt.atZone(ZONE).toLocalDate(), key -> new ArrayList<>())
                    .add((int) asLong(row[1]));
        }

        List<CsatReport.TrendPoint> trend = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Integer>> entry : byDate.entrySet()) {
            List<Integer> values = entry.getValue();
            double avg = round1(values.stream().mapToInt(Integer::intValue).average().orElse(0));
            trend.add(new CsatReport.TrendPoint(entry.getKey().toString(), avg, values.size()));
        }
        return trend;
    }

    private double slaComplianceRate(Instant since, Instant now) {
        long total = ticketRepository.countWithSlaSince(since);
        if (total == 0) {
            return 100.0;
        }
        long breached = ticketRepository.countSlaBreaches(since, now);
        return round1((double) (total - breached) / total * 100);
    }

    private Map<String, Double> periodMetrics(Instant start, Instant end) {
        long created = ticketRepository.countCreatedBetween(start, end);
        long resolved = ticketRepository.countResolvedBetween(start, end);
        double rate = created > 0 ? round1((double) resolved / created * 100) : 0.0;

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("total_created", (double) created);
        metrics.put("total_resolved", (double) resolved);
        metrics.put("resolution_rate", rate);
        return metrics;
    }

    private List<ReportSeriesPoint> toSeries(List<Object[]> rows) {
        List<ReportSeriesPoint> series = new ArrayList<>();
        for (Object[] row : rows) {
            series.add(new ReportSeriesPoint(label(row[0]), asLong(row[1])));
        }
        return series;
    }

    private double averageHours(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        for (Object[] row : rows) {
            sum += hoursBetween((Instant) row[0], (Instant) row[1]);
        }
        return sum / rows.size();
    }

    private Instant since(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private static List<ResponseTimeReport.HistogramBucket> buildHistogram(List<Double> values,
                                                                           Map<String, double[]> buckets) {
        int total = values.size();
        List<ResponseTimeReport.HistogramBucket> result = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : buckets.entrySet()) {
            double min = entry.getValue()[0];
            double max = entry.getValue()[1];
            long count = values.stream()
                    .filter(v -> v >= min && (v < max || max == Double.POSITIVE_INFINITY))
                    .count();
            double percentage = total > 0 ? round1((double) count / total * 100) : 0.0;
            result.add(new ResponseTimeReport.HistogramBucket(entry.getKey(), count, percentage));
        }
        return result;
    }

    private static Map<String, double[]> frtBuckets() {
        Map<String, double[]> buckets = new LinkedHashMap<>();
        buckets.put("<1h", new double[] {0, 1});
        buckets.put("1-4h", new double[] {1, 4});
        buckets.put("4-8h", new double[] {4, 8});
        buckets.put("8-24h", new double[] {8, 24});
        buckets.put(">24h", new double[] {24, Double.POSITIVE_INFINITY});
        return buckets;
    }

    private static Map<String, double[]> resolutionBuckets() {
        Map<String, double[]> buckets = new LinkedHashMap<>();
        buckets.put("<4h", new double[] {0, 4});
        buckets.put("4-8h", new double[] {4, 8});
        buckets.put("8-24h", new double[] {8, 24});
        buckets.put("1-3d", new double[] {24, 72});
        buckets.put("3-7d", new double[] {72, 168});
        buckets.put(">7d", new double[] {168, Double.POSITIVE_INFINITY});
        return buckets;
    }

    private static double hoursBetween(Instant from, Instant to) {
        return (to.toEpochMilli() - from.toEpochMilli()) / 3_600_000.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String label(Object value) {
        return value == null ? "unknown" : value.toString();
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private static double asDouble(Object value) {
        return ((Number) value).doubleValue();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Pure math helpers (unit-tested)
    // ──────────────────────────────────────────────────────────────────────

    public static Map<String, Double> calculatePercentiles(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("p50", percentileValue(sorted, 50));
        result.put("p75", percentileValue(sorted, 75));
        result.put("p90", percentileValue(sorted, 90));
        result.put("p95", percentileValue(sorted, 95));
        result.put("p99", percentileValue(sorted, 99));
        return result;
    }

    public static double percentileValue(List<Double> sorted, double p) {
        if (sorted.size() == 1) {
            return Math.round(sorted.get(0) * 100.0) / 100.0;
        }
        double k = (p / 100) * (sorted.size() - 1);
        int f = (int) Math.floor(k);
        int c = (int) Math.ceil(k);
        if (f == c) {
            return Math.round(sorted.get(f) * 100.0) / 100.0;
        }
        return Math.round((sorted.get(f) + (k - f) * (sorted.get(c) - sorted.get(f))) * 100.0) / 100.0;
    }

    public static double compositeScore(double resolutionRate, Double avgFrt, Double avgResolution, Double avgCsat) {
        double score = 0;
        double weights = 0;
        score += (resolutionRate / 100) * 30;
        weights += 30;
        if (avgFrt != null && avgFrt > 0) {
            score += Math.max(1 - avgFrt / 24, 0) * 25;
            weights += 25;
        }
        if (avgResolution != null && avgResolution > 0) {
            score += Math.max(1 - avgResolution / 72, 0) * 25;
            weights += 25;
        }
        if (avgCsat != null) {
            score += (avgCsat / 5) * 20;
            weights += 20;
        }
        return weights > 0 ? Math.round((score / weights) * 1000.0) / 10.0 : 0;
    }

    public static List<LocalDate> dateSeries(LocalDate from, LocalDate to) {
        int days = Math.min(Math.max((int) ChronoUnit.DAYS.between(from, to) + 1, 1), 90);
        return IntStream.range(0, days).mapToObj(from::plusDays).collect(Collectors.toList());
    }

    public static Map<String, Double> calculateChanges(Map<String, Double> current, Map<String, Double> previous) {
        Map<String, Double> changes = new LinkedHashMap<>();
        for (String key : List.of("total_created", "total_resolved", "resolution_rate")) {
            double cur = current.getOrDefault(key, 0.0);
            double prev = previous.getOrDefault(key, 0.0);
            changes.put(key, prev == 0 ? (cur > 0 ? 100.0 : 0.0) : Math.round((cur - prev) / prev * 1000.0) / 10.0);
        }
        return changes;
    }
}
