package dev.escalated.dto.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Response-time analytics reused for both first-response and resolution
 * reports: a bucketed histogram, percentiles (p50/p75/p90/p95/p99), and a
 * per-day trend of average hours.
 */
public record ResponseTimeReport(
        @JsonProperty("period_days") int periodDays,
        @JsonProperty("total_measured") long totalMeasured,
        @JsonProperty("avg_hours") double avgHours,
        List<HistogramBucket> distribution,
        Map<String, Double> percentiles,
        List<TrendPoint> trend) {

    public record HistogramBucket(String bucket, long count, double percentage) {
    }

    public record TrendPoint(
            String date,
            @JsonProperty("avg_hours") double avgHours,
            long count) {
    }
}
