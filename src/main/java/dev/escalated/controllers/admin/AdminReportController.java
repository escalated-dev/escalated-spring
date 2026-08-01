package dev.escalated.controllers.admin;

import dev.escalated.dto.reporting.AgentPerformanceReport;
import dev.escalated.dto.reporting.CsatReport;
import dev.escalated.dto.reporting.OverviewReport;
import dev.escalated.dto.reporting.PeriodComparisonReport;
import dev.escalated.dto.reporting.ResponseTimeReport;
import dev.escalated.dto.reporting.SlaReport;
import dev.escalated.dto.reporting.VolumeReport;
import dev.escalated.services.AdvancedReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-secured analytics endpoints. Sits behind the {@code /escalated/api/**}
 * security filter chain (token-authenticated) like every other admin
 * controller, and returns JSON report DTOs produced by
 * {@link AdvancedReportingService}. The {@code days} query parameter selects
 * the trailing window (defaults to 30).
 */
@RestController
@RequestMapping("/escalated/api/admin/reports")
public class AdminReportController {

    private final AdvancedReportingService reporting;

    public AdminReportController(AdvancedReportingService reporting) {
        this.reporting = reporting;
    }

    @GetMapping("/overview")
    public ResponseEntity<OverviewReport> overview(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(reporting.overview(days));
    }

    @GetMapping("/sla")
    public ResponseEntity<SlaReport> sla(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(reporting.slaReport(days));
    }

    @GetMapping("/first-response-time")
    public ResponseEntity<ResponseTimeReport> firstResponseTime(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(reporting.firstResponseTime(days));
    }

    @GetMapping("/resolution-time")
    public ResponseEntity<ResponseTimeReport> resolutionTime(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(reporting.resolutionTime(days));
    }

    @GetMapping("/csat")
    public ResponseEntity<CsatReport> csat(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(reporting.csat(days));
    }

    @GetMapping("/volume")
    public ResponseEntity<VolumeReport> volume(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(reporting.volume(days));
    }

    @GetMapping("/agent-performance")
    public ResponseEntity<AgentPerformanceReport> agentPerformance(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(reporting.agentPerformance(days));
    }

    @GetMapping("/period-comparison")
    public ResponseEntity<PeriodComparisonReport> periodComparison(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(reporting.periodComparison(days));
    }
}
