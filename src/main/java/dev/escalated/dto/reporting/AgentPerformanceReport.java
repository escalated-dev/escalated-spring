package dev.escalated.dto.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Ranked agent performance. Each row carries a composite score (resolution
 * rate, first-response time, CSAT) computed by
 * {@code AdvancedReportingService.compositeScore}.
 */
public record AgentPerformanceReport(
        @JsonProperty("period_days") int periodDays,
        List<AgentRow> agents) {

    public record AgentRow(
            @JsonProperty("agent_id") long agentId,
            @JsonProperty("agent_name") String agentName,
            @JsonProperty("total_tickets") long totalTickets,
            @JsonProperty("resolved_tickets") long resolvedTickets,
            @JsonProperty("resolution_rate") double resolutionRate,
            @JsonProperty("avg_response_hours") double avgResponseHours,
            @JsonProperty("csat_average") double csatAverage,
            @JsonProperty("composite_score") double compositeScore,
            int rank) {
    }
}
