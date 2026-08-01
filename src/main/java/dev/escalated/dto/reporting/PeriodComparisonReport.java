package dev.escalated.dto.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Current period vs. the immediately preceding period of equal length, with
 * percentage changes computed by
 * {@code AdvancedReportingService.calculateChanges}.
 */
public record PeriodComparisonReport(
        @JsonProperty("period_days") int periodDays,
        Map<String, Double> current,
        Map<String, Double> previous,
        Map<String, Double> changes) {
}
