package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.dto.reporting.AgentPerformanceReport;
import dev.escalated.dto.reporting.CsatReport;
import dev.escalated.dto.reporting.OverviewReport;
import dev.escalated.dto.reporting.PeriodComparisonReport;
import dev.escalated.dto.reporting.ResponseTimeReport;
import dev.escalated.dto.reporting.SlaReport;
import dev.escalated.dto.reporting.VolumeReport;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.SatisfactionRating;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.SatisfactionRatingRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves the reporting aggregation queries + math produce correct, populated
 * reports over a deterministic set of seeded tickets and ratings.
 */
@DataJpaTest
@Import(AdvancedReportingService.class)
@TestPropertySource(
        properties = {
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.flyway.enabled=false",
        })
class AdvancedReportingServiceDbTest {

    private static final double DELTA = 0.05;

    @Autowired private TicketRepository tickets;
    @Autowired private SatisfactionRatingRepository ratings;
    @Autowired private AgentProfileRepository agents;
    @Autowired private SlaPolicyRepository slaPolicies;
    @Autowired private AdvancedReportingService reporting;

    private AgentProfile alice;
    private AgentProfile bob;

    @BeforeEach
    void seed() {
        alice = agent("Alice", "alice@example.com");
        bob = agent("Bob", "bob@example.com");
        SlaPolicy sla = slaPolicy();

        // Ticket 1: resolved, met both SLAs. FRT 2h, resolution 10h.
        Ticket t1 = ticket("ESC-1", TicketPriority.HIGH, "email", TicketStatus.RESOLVED, alice, sla,
                2.0, 10.0, 4.0, 24.0);
        // Ticket 2: resolved, breached both SLAs. FRT 6h, resolution 30h.
        Ticket t2 = ticket("ESC-2", TicketPriority.HIGH, "email", TicketStatus.RESOLVED, alice, sla,
                6.0, 30.0, 4.0, 24.0);
        // Ticket 3: open, met FRT, resolution due in the future -> not breached. FRT 1h.
        Ticket t3 = ticket("ESC-3", TicketPriority.LOW, "web", TicketStatus.OPEN, bob, sla,
                1.0, null, 8.0, 48.0);
        // Ticket 4: open, no SLA policy, never responded.
        ticket("ESC-4", TicketPriority.MEDIUM, "chat", TicketStatus.OPEN, null, null,
                null, null, null, null);

        rating(t1, 5);
        rating(t2, 2);
        rating(t3, 4);
    }

    @Test
    void overview_summarisesSeededTickets() {
        OverviewReport report = reporting.overview(30);

        assertThat(report.periodDays()).isEqualTo(30);
        assertThat(report.totalTickets()).isEqualTo(4);
        assertThat(report.resolvedTickets()).isEqualTo(2);
        assertThat(report.resolutionRate()).isEqualTo(50.0);
        assertThat(report.avgFirstResponseHours()).isEqualTo(3.0, org.assertj.core.data.Offset.offset(DELTA));
        assertThat(report.avgResolutionHours()).isEqualTo(20.0, org.assertj.core.data.Offset.offset(DELTA));
        assertThat(report.slaComplianceRate()).isEqualTo(66.7, org.assertj.core.data.Offset.offset(DELTA));
        assertThat(report.csatAverage()).isEqualTo(3.7, org.assertj.core.data.Offset.offset(DELTA));

        assertThat(report.byStatus()).extracting("label").contains("RESOLVED", "OPEN");
        assertThat(report.byPriority()).extracting("label").contains("HIGH", "LOW", "MEDIUM");
        assertThat(report.byChannel()).extracting("label").contains("email", "web", "chat");
        assertThat(seriesTotal(report.byPriority())).isEqualTo(4);
    }

    @Test
    void slaReport_countsBreachesAndCompliance() {
        SlaReport report = reporting.slaReport(30);

        assertThat(report.totalWithSla()).isEqualTo(3);
        assertThat(report.firstResponseBreaches()).isEqualTo(1);
        assertThat(report.resolutionBreaches()).isEqualTo(1);
        assertThat(report.totalBreaches()).isEqualTo(1);
        assertThat(report.complianceRate()).isEqualTo(66.7, org.assertj.core.data.Offset.offset(DELTA));

        SlaReport.PriorityBreakdown high = report.byPriority().stream()
                .filter(p -> p.priority().equals("HIGH"))
                .findFirst()
                .orElseThrow();
        assertThat(high.total()).isEqualTo(2);
        assertThat(high.breached()).isEqualTo(1);
        assertThat(high.breachRate()).isEqualTo(50.0, org.assertj.core.data.Offset.offset(DELTA));
    }

    @Test
    void firstResponseTime_distributionAndPercentiles() {
        ResponseTimeReport report = reporting.firstResponseTime(30);

        assertThat(report.totalMeasured()).isEqualTo(3);
        assertThat(report.avgHours()).isEqualTo(3.0, org.assertj.core.data.Offset.offset(DELTA));
        assertThat(report.percentiles().get("p50")).isEqualTo(2.0, org.assertj.core.data.Offset.offset(DELTA));

        long bucketTotal = report.distribution().stream()
                .mapToLong(ResponseTimeReport.HistogramBucket::count)
                .sum();
        assertThat(bucketTotal).isEqualTo(3);
        assertThat(report.trend()).isNotEmpty();
    }

    @Test
    void resolutionTime_measuresResolvedTickets() {
        ResponseTimeReport report = reporting.resolutionTime(30);

        assertThat(report.totalMeasured()).isEqualTo(2);
        assertThat(report.avgHours()).isEqualTo(20.0, org.assertj.core.data.Offset.offset(DELTA));
    }

    @Test
    void csat_averagesAndBreaksDownRatings() {
        CsatReport report = reporting.csat(30);

        assertThat(report.totalRatings()).isEqualTo(3);
        assertThat(report.average()).isEqualTo(3.7, org.assertj.core.data.Offset.offset(DELTA));
        assertThat(report.breakdown()).isNotEmpty();
        assertThat(report.overTime()).isNotEmpty();
    }

    @Test
    void volume_producesZeroFilledSeriesAndCohorts() {
        VolumeReport report = reporting.volume(30);

        assertThat(report.totalTickets()).isEqualTo(4);
        assertThat(report.series()).isNotEmpty();
        assertThat(seriesTotal(report.series())).isEqualTo(4);
        assertThat(report.byPriority()).isNotEmpty();
        assertThat(report.byChannel()).isNotEmpty();
    }

    @Test
    void agentPerformance_ranksByCompositeScore() {
        AgentPerformanceReport report = reporting.agentPerformance(30);

        assertThat(report.agents()).hasSize(2);
        AgentPerformanceReport.AgentRow first = report.agents().get(0);
        AgentPerformanceReport.AgentRow second = report.agents().get(1);
        assertThat(first.rank()).isEqualTo(1);
        assertThat(second.rank()).isEqualTo(2);
        assertThat(first.compositeScore()).isGreaterThanOrEqualTo(second.compositeScore());
        assertThat(first.agentName()).isEqualTo("Alice");
        assertThat(first.totalTickets()).isEqualTo(2);
        assertThat(first.resolvedTickets()).isEqualTo(2);
    }

    @Test
    void periodComparison_reportsCurrentAndChanges() {
        PeriodComparisonReport report = reporting.periodComparison(30);

        assertThat(report.current().get("total_created")).isEqualTo(4.0);
        assertThat(report.changes()).containsKeys("total_created", "total_resolved", "resolution_rate");
    }

    // ── seeding helpers ──────────────────────────────────────────────────────

    private long seriesTotal(java.util.List<dev.escalated.dto.reporting.ReportSeriesPoint> series) {
        return series.stream().mapToLong(dev.escalated.dto.reporting.ReportSeriesPoint::value).sum();
    }

    private AgentProfile agent(String name, String email) {
        AgentProfile agent = new AgentProfile();
        agent.setName(name);
        agent.setEmail(email);
        return agents.saveAndFlush(agent);
    }

    private SlaPolicy slaPolicy() {
        SlaPolicy policy = new SlaPolicy();
        policy.setName("Standard");
        policy.setFirstResponseMinutes(240);
        policy.setResolutionMinutes(1440);
        return slaPolicies.saveAndFlush(policy);
    }

    private Ticket ticket(String number, TicketPriority priority, String channel, TicketStatus status,
                          AgentProfile agent, SlaPolicy sla,
                          Double frtHours, Double resolutionHours,
                          Double frtDueHours, Double resolutionDueHours) {
        Ticket ticket = new Ticket();
        ticket.setSubject("Subject " + number);
        ticket.setBody("Body");
        ticket.setTicketNumber(number);
        ticket.setRequesterName("Requester");
        ticket.setRequesterEmail("requester@example.com");
        ticket.setPriority(priority);
        ticket.setChannel(channel);
        ticket.setStatus(status);
        ticket.setAssignedAgent(agent);
        ticket.setSlaPolicy(sla);
        ticket = tickets.saveAndFlush(ticket);

        Instant created = ticket.getCreatedAt();
        if (frtHours != null) {
            ticket.setFirstRespondedAt(plusHours(created, frtHours));
        }
        if (resolutionHours != null) {
            ticket.setResolvedAt(plusHours(created, resolutionHours));
        }
        if (frtDueHours != null) {
            ticket.setSlaFirstResponseDueAt(plusHours(created, frtDueHours));
        }
        if (resolutionDueHours != null) {
            ticket.setSlaDueAt(plusHours(created, resolutionDueHours));
        }
        return tickets.saveAndFlush(ticket);
    }

    private void rating(Ticket ticket, int value) {
        SatisfactionRating rating = new SatisfactionRating();
        rating.setTicket(ticket);
        rating.setRating(value);
        rating.setRaterEmail("rater@example.com");
        ratings.saveAndFlush(rating);
    }

    private static Instant plusHours(Instant base, double hours) {
        return base.plusSeconds((long) (hours * 3600));
    }
}
