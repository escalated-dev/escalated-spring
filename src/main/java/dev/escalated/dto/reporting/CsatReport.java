package dev.escalated.dto.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Customer satisfaction analytics: average rating, response rate, a breakdown
 * by rating value, and a per-day trend of average rating.
 */
public record CsatReport(
        @JsonProperty("period_days") int periodDays,
        double average,
        @JsonProperty("total_ratings") long totalRatings,
        @JsonProperty("response_rate") double responseRate,
        List<ReportSeriesPoint> breakdown,
        @JsonProperty("over_time") List<TrendPoint> overTime) {

    public record TrendPoint(
            String date,
            @JsonProperty("avg_rating") double avgRating,
            long count) {
    }
}
