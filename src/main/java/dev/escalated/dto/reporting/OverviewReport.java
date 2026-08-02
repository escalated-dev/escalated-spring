package dev.escalated.dto.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Headline dashboard metrics for a reporting period: totals, average response
 * and resolution times, SLA compliance, CSAT, and grouped counts.
 */
public record OverviewReport(
        @JsonProperty("period_days") int periodDays,
        @JsonProperty("total_tickets") long totalTickets,
        @JsonProperty("resolved_tickets") long resolvedTickets,
        @JsonProperty("resolution_rate") double resolutionRate,
        @JsonProperty("avg_first_response_hours") double avgFirstResponseHours,
        @JsonProperty("avg_resolution_hours") double avgResolutionHours,
        @JsonProperty("sla_compliance_rate") double slaComplianceRate,
        @JsonProperty("csat_average") double csatAverage,
        @JsonProperty("by_status") List<ReportSeriesPoint> byStatus,
        @JsonProperty("by_priority") List<ReportSeriesPoint> byPriority,
        @JsonProperty("by_channel") List<ReportSeriesPoint> byChannel) {
}
