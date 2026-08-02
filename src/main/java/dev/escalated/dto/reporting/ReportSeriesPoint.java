package dev.escalated.dto.reporting;

/**
 * A single labelled data point used across reports (status/priority/channel
 * counts, daily volume, rating breakdowns). Maps directly to the
 * {@code {"label": ..., "value": ...}} shape the reporting frontend expects.
 */
public record ReportSeriesPoint(String label, long value) {
}
