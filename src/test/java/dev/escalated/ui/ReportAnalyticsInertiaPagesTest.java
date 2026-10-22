package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.SatisfactionRatingService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The analytics report screens over the Inertia protocol: SlaTrends, ResponseTimes,
 * ResolutionTimes, AgentRanking and Cohorts. Each test's tickets share a department, ticket type,
 * tag and assignee of their own, so their rows can be checked exactly beside other tests' tickets.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class ReportAnalyticsInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private DepartmentRepository departments;
    @Autowired private SlaPolicyRepository policies;
    @Autowired private TicketService tickets;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private SatisfactionRatingService ratings;

    private String unique;
    private AgentProfile admin;
    private AgentProfile agent;
    private Department department;
    private SlaPolicy policy;
    /** First response 2h after creation (1h late), resolved after 10h, rated 4. */
    private Ticket late;
    /** First response after 6h (on time), resolution due in 2h: at risk. */
    private Ticket atRisk;
    /** No first response, due an hour ago, still open: breached. */
    private Ticket overdue;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        admin = staff("Admin", true);
        agent = staff("Agent", false);
        department = new Department();
        department.setName("Cohort " + unique);
        department = departments.save(department);
        // Never selected for a ticket: inactive, and only attached here by hand.
        policy = new SlaPolicy();
        policy.setName("Analytics " + unique);
        policy.setActive(false);
        policy = policies.save(policy);

        Instant now = Instant.now();
        late = ticket("Late", 2, 10, 1, now.plus(30, ChronoUnit.DAYS));
        atRisk = ticket("At risk", 6, null, 8, now.plus(2, ChronoUnit.HOURS));
        overdue = ticket("Overdue", null, null, null, null);
        Ticket stored = ticketRepository.findById(overdue.getId()).orElseThrow();
        stored.setSlaFirstResponseDueAt(now.minus(1, ChronoUnit.HOURS));
        overdue = ticketRepository.save(stored);
        ratings.rateTicket(late.getId(), 4, "Fine");
    }

    @Test
    void anAgentWhoIsNotAnAdminIsRefused() throws Exception {
        for (String report : new String[] {"sla-trends", "response-times", "resolution-times", "agent-ranking",
                "cohorts"}) {
            mvc.perform(get("/escalated/admin/reports/" + report).with(user(agent.getEmail())))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void slaTrendsCarryTheCountsTrendsRatesAndTicketsAtRisk() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/reports/sla-trends").param("days", "7"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Reports/SlaTrends");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("period_days", "breach_trend", "breach_by_type_trend",
                "breach_by_department", "breach_by_priority", "at_risk_tickets", "total_breaches", "breach_rate",
                "first_response_breaches", "resolution_breaches");
        assertThat(props.get("period_days").asInt()).isEqualTo(7);
        assertThat(props.get("total_breaches").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(props.get("first_response_breaches").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(props.get("breach_rate").isNumber()).isTrue();

        JsonNode today = props.get("breach_trend").get(props.get("breach_trend").size() - 1);
        assertThat(fieldNames(today)).containsExactly("label", "value");
        assertThat(today.get("value").asLong()).isGreaterThanOrEqualTo(2);
        JsonNode stacked = props.get("breach_by_type_trend").get(props.get("breach_by_type_trend").size() - 1);
        assertThat(fieldNames(stacked)).containsExactly("label", "values");
        assertThat(stacked.get("values").size()).isEqualTo(2);
        assertThat(stacked.get("label").asText()).isEqualTo(today.get("label").asText());

        // Two of this department's three SLA tickets breached.
        assertThat(point(props.get("breach_by_department"), "Cohort " + unique).get("value").asDouble())
                .isEqualTo(66.7);
        assertThat(point(props.get("breach_by_priority"), "high").get("value").isNumber()).isTrue();

        JsonNode risk = null;
        for (JsonNode ticket : props.get("at_risk_tickets")) {
            if (ticket.get("id").asLong() == atRisk.getId()) {
                risk = ticket;
            }
            assertThat(ticket.get("id").asLong()).isNotEqualTo(late.getId());
        }
        assertThat(risk).as("the ticket due in two hours").isNotNull();
        assertThat(fieldNames(risk)).containsExactly("id", "reference", "subject", "hours_remaining");
        assertThat(risk.get("reference").asText()).isEqualTo(atRisk.getTicketNumber());
        assertThat(risk.get("hours_remaining").asDouble()).isBetween(1.9, 2.0);

        JsonNode weekly = inertia(get("/escalated/admin/reports/sla-trends").param("group_by", "week")).get("props");
        assertThat(weekly.get("breach_trend").get(0).get("label").asText()).matches("\\d{4}-W\\d{2}");
        JsonNode monthly = inertia(get("/escalated/admin/reports/sla-trends").param("group_by", "month")).get("props");
        assertThat(monthly.get("breach_trend").get(0).get("label").asText()).matches("\\d{4}-\\d{2}");
    }

    @Test
    void responseTimesCarryTheSummaryDistributionAndBreakdowns() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/reports/response-times"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Reports/ResponseTimes");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("period_days", "avg_frt", "median_frt", "p90_frt",
                "pct_under_target", "target_hours", "distribution", "trend", "by_agent", "by_department",
                "by_priority");
        assertThat(props.get("target_hours").asInt()).isEqualTo(4);
        assertThat(labels(props.get("distribution"))).containsExactly("<1h", "1-4h", "4-8h", "8-24h", ">24h");
        assertThat(props.get("trend").get(0).has("label")).isTrue();

        JsonNode row = agentRow(props.get("by_agent"));
        assertThat(fieldNames(row)).containsExactly("agent_id", "agent_name", "count", "avg", "median", "p90");
        assertThat(row.get("agent_name").asText()).isEqualTo(agent.getName());
        assertThat(row.get("count").asInt()).isEqualTo(2);
        assertThat(row.get("avg").asDouble()).isEqualTo(4.0);
        assertThat(row.get("median").asDouble()).isEqualTo(4.0);
        assertThat(row.get("p90").asDouble()).isEqualTo(5.6);
        assertThat(point(props.get("by_department"), "Cohort " + unique).get("value").asDouble()).isEqualTo(4.0);
        assertThat(point(props.get("by_priority"), "high").get("value").isNumber()).isTrue();

        // Laravel's older name for the screen.
        assertThat(inertia(get("/escalated/admin/reports/frt")).get("component").asText())
                .isEqualTo("Escalated/Admin/Reports/ResponseTimes");
    }

    @Test
    void resolutionTimesCarryTheSummaryDistributionAndBreakdowns() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/reports/resolution-times"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Reports/ResolutionTimes");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("period_days", "avg_resolution", "median_resolution",
                "p90_resolution", "pct_under_target", "target_hours", "distribution", "trend", "by_agent",
                "by_department", "by_channel");
        assertThat(props.get("target_hours").asInt()).isEqualTo(24);
        assertThat(labels(props.get("distribution"))).containsExactly("<4h", "4-8h", "8-24h", "1-3d", "3-7d", ">7d");
        JsonNode row = agentRow(props.get("by_agent"));
        assertThat(row.get("count").asInt()).isEqualTo(1);
        assertThat(row.get("avg").asDouble()).isEqualTo(10.0);
        assertThat(point(props.get("by_department"), "Cohort " + unique).get("value").asDouble()).isEqualTo(10.0);
        assertThat(props.get("by_channel").get(0).has("label")).isTrue();
        assertThat(inertia(get("/escalated/admin/reports/resolution")).get("component").asText())
                .isEqualTo("Escalated/Admin/Reports/ResolutionTimes");
    }

    @Test
    void theAgentRankingScoresAsLaravelDoes() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/reports/agent-ranking"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Reports/AgentRanking");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("period_days", "agents");
        JsonNode row = agentRow(props.get("agents"));
        assertThat(fieldNames(row)).containsExactly("agent_id", "agent_name", "volume", "resolution_rate", "avg_frt",
                "avg_resolution", "csat", "composite_score");
        assertThat(row.get("volume").asLong()).isEqualTo(3);
        assertThat(row.get("resolution_rate").asDouble()).isEqualTo(33.3);
        assertThat(row.get("avg_frt").asDouble()).isEqualTo(4.0);
        assertThat(row.get("avg_resolution").asDouble()).isEqualTo(10.0);
        assertThat(row.get("csat").asDouble()).isEqualTo(4.0);
        // 33.3% x 0.30 + (100 - 4 x 5) x 0.25 + (4 x 20) x 0.25 + (3 x 2) x 0.20
        assertThat(row.get("composite_score").asDouble()).isEqualTo(51.2);

        List<Double> scores = new ArrayList<>();
        props.get("agents").forEach(agentRow -> scores.add(agentRow.get("composite_score").asDouble()));
        assertThat(scores).isSortedAccordingTo((a, b) -> Double.compare(b, a));
    }

    @Test
    void cohortsCarryVolumeResolutionBreachRateAndCsatPerGroup() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/reports/cohorts"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Reports/Cohorts");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("period_days", "by_tag", "by_department", "by_channel",
                "by_type", "by_priority");
        for (String[] cohort : new String[][] {{"by_tag", "cohort-" + unique}, {"by_department", "Cohort " + unique},
                {"by_type", "type-" + unique}}) {
            JsonNode row = named(props.get(cohort[0]), cohort[1]);
            assertThat(fieldNames(row)).containsExactly("name", "volume", "avg_resolution", "breach_rate", "csat");
            assertThat(row.get("volume").asLong()).as(cohort[0]).isEqualTo(3);
            assertThat(row.get("avg_resolution").asDouble()).as(cohort[0]).isEqualTo(10.0);
            assertThat(row.get("breach_rate").asDouble()).as(cohort[0]).isEqualTo(66.7);
            assertThat(row.get("csat").asDouble()).as(cohort[0]).isEqualTo(4.0);
        }
        assertThat(named(props.get("by_priority"), "high").get("volume").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(props.get("by_channel").size()).isPositive();
    }

    private Ticket ticket(String subject, Integer respondedAfterHours, Integer resolvedAfterHours,
                          Integer firstResponseDueHours, Instant resolutionDue) {
        Ticket ticket = tickets.create(subject + " " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.HIGH, department.getId(), "type-" + unique);
        tickets.assign(ticket.getId(), agent.getId(), admin.getEmail());
        tickets.addTag(ticket.getId(), "cohort-" + unique);
        Ticket stored = ticketRepository.findById(ticket.getId()).orElseThrow();
        Instant created = stored.getCreatedAt();
        stored.setSlaPolicy(policy);
        stored.setSlaFirstResponseDueAt(firstResponseDueHours == null ? null
                : created.plus(firstResponseDueHours, ChronoUnit.HOURS));
        stored.setSlaDueAt(resolutionDue);
        stored.setFirstRespondedAt(respondedAfterHours == null ? null
                : created.plus(respondedAfterHours, ChronoUnit.HOURS));
        stored.setResolvedAt(resolvedAfterHours == null ? null : created.plus(resolvedAfterHours, ChronoUnit.HOURS));
        return ticketRepository.save(stored);
    }

    private AgentProfile staff(String role, boolean isAdmin) {
        AgentProfile profile = new AgentProfile();
        profile.setName(role + " " + unique);
        profile.setEmail(role.toLowerCase() + "-" + unique + "@example.com");
        profile.setAgent(true);
        profile.setAdmin(isAdmin);
        return agents.save(profile);
    }

    private JsonNode agentRow(JsonNode rows) {
        for (JsonNode row : rows) {
            if (row.get("agent_id").asLong() == agent.getId()) {
                return row;
            }
        }
        throw new AssertionError(agent.getId() + " not in " + rows);
    }

    private static JsonNode point(JsonNode series, String label) {
        for (JsonNode point : series) {
            if (point.get("label").asText().equals(label)) {
                return point;
            }
        }
        throw new AssertionError(label + " not in " + series);
    }

    private static JsonNode named(JsonNode rows, String name) {
        for (JsonNode row : rows) {
            if (row.get("name").asText().equals(name)) {
                return row;
            }
        }
        throw new AssertionError(name + " not in " + rows);
    }

    private static List<String> labels(JsonNode series) {
        List<String> labels = new ArrayList<>();
        series.forEach(point -> labels.add(point.get("label").asText()));
        return labels;
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.with(user(admin.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
