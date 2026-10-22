package dev.escalated.ui.controllers;

import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.ReportAnalyticsUiPages;
import dev.escalated.ui.ReportsUiPages;
import dev.escalated.ui.UiRoutes;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The report screens, as escalated-laravel's {@code Admin\ReportController}:
 * {@code Escalated/Admin/Reports} and {@code Reports/Dashboard}, {@code AgentMetrics},
 * {@code SlaReport}, {@code CsatReport}, {@code Comparison}, {@code SlaTrends}, {@code ResponseTimes},
 * {@code ResolutionTimes}, {@code AgentRanking}, {@code AgentDetail} and {@code Cohorts}. Each takes {@code days} (or
 * {@code period}), default 30. Admins only (the web security chain).
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin/reports")
public class ReportsUiController {

    private final EscalatedUiRenderer renderer;
    private final ReportsUiPages pages;
    private final ReportAnalyticsUiPages analytics;

    public ReportsUiController(EscalatedUiRenderer renderer, ReportsUiPages pages, ReportAnalyticsUiPages analytics) {
        this.renderer = renderer;
        this.pages = pages;
        this.analytics = analytics;
    }

    @GetMapping({"", "/"})
    public Object overview(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports", pages.overview(ReportsUiPages.days(query)));
    }

    @GetMapping("/dashboard")
    public Object dashboard(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports/Dashboard", pages.dashboard(ReportsUiPages.days(query)));
    }

    @GetMapping("/agents")
    public Object agents(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports/AgentMetrics", pages.agents(ReportsUiPages.days(query)));
    }

    @GetMapping("/sla")
    public Object sla(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports/SlaReport", pages.sla(ReportsUiPages.days(query)));
    }

    @GetMapping("/comparison")
    public Object comparison(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports/Comparison", pages.comparison(ReportsUiPages.days(query)));
    }

    @GetMapping("/csat")
    public Object csat(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports/CsatReport", pages.csat(ReportsUiPages.days(query)));
    }

    /** {@code group_by}: {@code day} (default), {@code week} or {@code month}. */
    @GetMapping("/sla-trends")
    public Object slaTrends(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports/SlaTrends",
                analytics.slaTrends(ReportsUiPages.days(query), query.getOrDefault("group_by", "day")));
    }

    @GetMapping({"/response-times", "/frt"})
    public Object responseTimes(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports/ResponseTimes", analytics.responseTimes(ReportsUiPages.days(query)));
    }

    @GetMapping({"/resolution-times", "/resolution"})
    public Object resolutionTimes(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports/ResolutionTimes",
                analytics.resolutionTimes(ReportsUiPages.days(query)));
    }

    @GetMapping("/agent-ranking")
    public Object agentRanking(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports/AgentRanking", analytics.agentRanking(ReportsUiPages.days(query)));
    }

    @GetMapping("/agent/{id:\\d+}/detail")
    public Object agentDetail(@PathVariable("id") Long id, @RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports/AgentDetail",
                analytics.agentDetail(ReportsUiPages.days(query), id));
    }

    @GetMapping("/cohorts")
    public Object cohorts(@RequestParam Map<String, String> query) {
        return renderer.render("Escalated/Admin/Reports/Cohorts", analytics.cohorts(ReportsUiPages.days(query)));
    }
}
