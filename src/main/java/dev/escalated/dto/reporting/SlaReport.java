package dev.escalated.dto.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * SLA compliance and breach trends over a period, with a per-priority
 * breakdown of breach rates.
 */
public record SlaReport(
        @JsonProperty("period_days") int periodDays,
        @JsonProperty("compliance_rate") double complianceRate,
        @JsonProperty("total_with_sla") long totalWithSla,
        @JsonProperty("first_response_breaches") long firstResponseBreaches,
        @JsonProperty("resolution_breaches") long resolutionBreaches,
        @JsonProperty("total_breaches") long totalBreaches,
        @JsonProperty("by_priority") List<PriorityBreakdown> byPriority) {

    public record PriorityBreakdown(
            String priority,
            long total,
            long breached,
            @JsonProperty("breach_rate") double breachRate) {
    }
}
