package dev.escalated.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AdvancedReportingService {

    public static Map<String, Double> calculatePercentiles(List<Double> values) {
        if (values == null || values.isEmpty()) return Collections.emptyMap();
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
        if (sorted.size() == 1) return Math.round(sorted.get(0) * 100.0) / 100.0;
        double k = (p / 100) * (sorted.size() - 1);
        int f = (int) Math.floor(k);
        int c = (int) Math.ceil(k);
        if (f == c) return Math.round(sorted.get(f) * 100.0) / 100.0;
        return Math.round((sorted.get(f) + (k - f) * (sorted.get(c) - sorted.get(f))) * 100.0) / 100.0;
    }

    public static double compositeScore(double resolutionRate, Double avgFrt, Double avgResolution, Double avgCsat) {
        double score = 0, weights = 0;
        score += (resolutionRate / 100) * 30; weights += 30;
        if (avgFrt != null && avgFrt > 0) { score += Math.max(1 - avgFrt / 24, 0) * 25; weights += 25; }
        if (avgResolution != null && avgResolution > 0) { score += Math.max(1 - avgResolution / 72, 0) * 25; weights += 25; }
        if (avgCsat != null) { score += (avgCsat / 5) * 20; weights += 20; }
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
