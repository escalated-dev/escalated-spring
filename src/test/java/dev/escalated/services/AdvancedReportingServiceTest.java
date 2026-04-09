package dev.escalated.services;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedReportingServiceTest {

    @Test
    void testCalculatePercentiles() {
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);
        Map<String, Double> result = AdvancedReportingService.calculatePercentiles(values);
        assertEquals(5.5, result.get("p50"));
        assertTrue(result.containsKey("p75"));
        assertTrue(result.containsKey("p90"));
        assertTrue(result.containsKey("p95"));
        assertTrue(result.containsKey("p99"));
    }

    @Test
    void testCalculatePercentilesEmpty() {
        assertTrue(AdvancedReportingService.calculatePercentiles(Collections.emptyList()).isEmpty());
    }

    @Test
    void testCompositeScore() {
        double score = AdvancedReportingService.compositeScore(80, 2.0, 24.0, 4.5);
        assertTrue(score > 0);
    }

    @Test
    void testDateSeries() {
        List<LocalDate> dates = AdvancedReportingService.dateSeries(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10));
        assertEquals(10, dates.size());
    }

    @Test
    void testDateSeriesCaps() {
        List<LocalDate> dates = AdvancedReportingService.dateSeries(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        assertEquals(90, dates.size());
    }

    @Test
    void testCalculateChanges() {
        Map<String, Double> current = Map.of("total_created", 100.0, "total_resolved", 80.0, "resolution_rate", 80.0);
        Map<String, Double> previous = Map.of("total_created", 50.0, "total_resolved", 40.0, "resolution_rate", 80.0);
        Map<String, Double> changes = AdvancedReportingService.calculateChanges(current, previous);
        assertEquals(100.0, changes.get("total_created"));
    }
}
