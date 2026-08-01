package dev.escalated.dto.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Ticket-volume analytics: a zero-filled daily series plus cohort breakdowns
 * by priority and channel.
 */
public record VolumeReport(
        @JsonProperty("period_days") int periodDays,
        @JsonProperty("total_tickets") long totalTickets,
        List<ReportSeriesPoint> series,
        @JsonProperty("by_priority") List<ReportSeriesPoint> byPriority,
        @JsonProperty("by_channel") List<ReportSeriesPoint> byChannel) {
}
