package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.SatisfactionRatingService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * The report screens over the Inertia protocol. The database is shared with the other tests, so
 * figures are checked on this test's own agent and SLA policy, and totals as at least what it added.
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
class ReportsInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private TicketService tickets;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private SlaPolicyRepository policies;
    @Autowired private SatisfactionRatingService ratings;

    private String unique;
    private AgentProfile admin;
    private AgentProfile agent;
    private SlaPolicy policy;
    private Ticket met;
    private Ticket breached;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin = agents.save(admin);

        agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent = agents.save(agent);

        // Scoped to a department no ticket has, so it is never applied to anyone else's tickets.
        policy = new SlaPolicy();
        policy.setName("Gold " + unique);
        policy.setPriority(TicketPriority.HIGH);
        policy.setFirstResponseMinutes(60);
        policy.setResolutionMinutes(480);
        policy.setDepartmentId(-1L);
        policy = policies.save(policy);

        met = ticket("Met " + unique, 2, 5, Instant.now().plus(3, ChronoUnit.HOURS));
        breached = ticket("Breached " + unique, 3, null, Instant.now().minus(1, ChronoUnit.HOURS));
        ratings.rateTicket(met.getId(), 5, "Great");
        ratings.rateTicket(breached.getId(), 2, "Slow");
    }

    @AfterEach
    void retirePolicy() {
        policy.setActive(false);
        policies.save(policy);
    }

    @Test
    void anAgentWhoIsNotAnAdminIsRefused() throws Exception {
        for (String path : new String[] {"/escalated/admin/reports", "/escalated/admin/reports/dashboard",
                "/escalated/admin/reports/agents", "/escalated/admin/reports/sla", "/escalated/admin/reports/csat"}) {
            mvc.perform(get(path).with(user(agent.getEmail()))).andExpect(status().isForbidden());
        }
    }

    @Test
    void theOverviewCarriesWhatTheReportsPageReads() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/reports").param("days", "7"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Reports");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("period_days", "total_tickets", "resolved_tickets",
                "avg_first_response_hours", "sla_breach_count", "by_status", "by_priority", "csat");
        assertThat(props.get("period_days").asInt()).isEqualTo(7);
        assertThat(props.get("total_tickets").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(props.get("sla_breach_count").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(props.get("by_status").isObject()).isTrue();
        assertThat(props.get("by_priority").get("high").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(fieldNames(props.get("csat"))).containsExactly("average", "total", "breakdown");
        assertThat(props.get("csat").get("breakdown").get("5").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void theDashboardCarriesItsSeriesAndThisAgentsFigures() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/reports/dashboard"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Reports/Dashboard");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("period_days", "volume", "by_status", "by_priority",
                "avg_response_hours", "avg_resolution_hours", "sla_compliance", "csat_average", "agent_performance");
        assertThat(props.get("period_days").asInt()).isEqualTo(30);
        assertThat(fieldNames(props.get("volume").get(0))).containsExactly("label", "value");
        assertThat(props.get("volume").get(props.get("volume").size() - 1).get("label").asText())
                .matches("[0-9]{4}-[0-9]{2}-[0-9]{2}");
        assertThat(fieldNames(props.get("by_status").get(0))).containsExactly("label", "value");

        JsonNode row = agentRow(props.get("agent_performance"));
        assertThat(fieldNames(row)).contains("agent_id", "agent_name", "total_tickets", "resolved_tickets",
                "avg_response_hours", "avg_resolution_hours");
        assertThat(row.get("agent_name").asText()).isEqualTo(agent.getName());
        assertThat(row.get("total_tickets").asLong()).isEqualTo(2);
        assertThat(row.get("resolved_tickets").asLong()).isEqualTo(1);
        assertThat(row.get("avg_response_hours").asDouble()).isEqualTo(2.5);
        assertThat(row.get("avg_resolution_hours").asDouble()).isEqualTo(5.0);

        JsonNode agentsPage = inertia(get("/escalated/admin/reports/agents"));
        assertThat(agentsPage.get("component").asText()).isEqualTo("Escalated/Admin/Reports/AgentMetrics");
        assertThat(fieldNames(agentsPage.get("props"))).contains("period_days", "agents");
        assertThat(agentRow(agentsPage.get("props").get("agents")).get("total_tickets").asLong()).isEqualTo(2);
    }

    @Test
    void theSlaReportShowsThePolicyAndTheBreach() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/reports/sla"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Reports/SlaReport");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("period_days", "compliance_rate", "compliance_by_policy", "breaches");
        JsonNode gold = null;
        for (JsonNode row : props.get("compliance_by_policy")) {
            if (row.get("policy_name").asText().equals("Gold " + unique)) {
                gold = row;
            }
        }
        assertThat(gold).isNotNull();
        assertThat(fieldNames(gold)).containsExactly("policy_name", "total", "met", "breached");
        assertThat(gold.get("total").asInt()).isEqualTo(2);
        assertThat(gold.get("met").asInt()).isEqualTo(1);
        assertThat(gold.get("breached").asInt()).isEqualTo(1);

        JsonNode breach = null;
        for (JsonNode row : props.get("breaches")) {
            if (row.get("id").asLong() == breached.getId()) {
                breach = row;
            }
        }
        assertThat(breach).isNotNull();
        assertThat(fieldNames(breach)).contains("id", "reference", "subject", "sla_first_response_breached",
                "sla_resolution_breached", "assignee", "sla_policy", "created_at");
        assertThat(breach.get("sla_first_response_breached").asBoolean()).isTrue();
        assertThat(breach.get("sla_policy").get("name").asText()).isEqualTo("Gold " + unique);
        for (JsonNode row : props.get("breaches")) {
            assertThat(row.get("id").asLong()).isNotEqualTo(met.getId());
        }
    }

    @Test
    void theCsatReportShowsThisAgentsRatings() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/reports/csat"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Reports/CsatReport");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("period_days", "csat_average", "response_rate", "total_ratings",
                "by_agent", "over_time");
        JsonNode row = agentRow(props.get("by_agent"));
        assertThat(fieldNames(row)).containsExactly("agent_id", "agent_name", "avg_rating", "total_ratings");
        assertThat(row.get("avg_rating").asDouble()).isEqualTo(3.5);
        assertThat(row.get("total_ratings").asInt()).isEqualTo(2);
        assertThat(fieldNames(props.get("over_time").get(0))).containsExactly("label", "value");
        assertThat(props.get("total_ratings").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void theComparisonSetsThisPeriodBesideTheOneBefore() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/reports/comparison").param("days", "14"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Reports/Comparison");
        JsonNode props = page.get("props");
        assertThat(props.get("period_days").asInt()).isEqualTo(14);
        for (String side : new String[] {"current", "previous"}) {
            assertThat(fieldNames(props.get(side))).containsExactly("total_tickets", "resolved_tickets", "avg_frt",
                    "avg_resolution", "sla_compliance", "csat", "breach_count", "volume_trend");
        }
        assertThat(props.get("current").get("total_tickets").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(props.get("current").get("breach_count").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(fieldNames(props.get("current").get("volume_trend").get(0))).containsExactly("label", "value");
    }

    @Test
    void thePeriodIsClampedAndDefaultsToThirtyDays() throws Exception {
        assertThat(inertia(get("/escalated/admin/reports/sla").param("days", "9999")).get("props").get("period_days")
                .asInt()).isEqualTo(365);
        assertThat(inertia(get("/escalated/admin/reports/csat").param("period", "3").param("days", "9")).get("props")
                .get("period_days").asInt()).isEqualTo(3);
        assertThat(inertia(get("/escalated/admin/reports/agents").param("days", "soon")).get("props")
                .get("period_days").asInt()).isEqualTo(30);
    }

    private Ticket ticket(String subject, int respondedAfterHours, Integer resolvedAfterHours, Instant firstDue) {
        Ticket ticket = tickets.create(subject, "b", "R", "r-" + unique + "@example.com", TicketPriority.HIGH, null);
        tickets.assign(ticket.getId(), agent.getId(), admin.getEmail());
        Ticket stored = ticketRepository.findById(ticket.getId()).orElseThrow();
        Instant created = stored.getCreatedAt();
        stored.setSlaPolicy(policy);
        stored.setSlaFirstResponseDueAt(firstDue);
        stored.setSlaDueAt(created.plus(30, ChronoUnit.DAYS));
        stored.setFirstRespondedAt(created.plus(respondedAfterHours, ChronoUnit.HOURS));
        if (resolvedAfterHours != null) {
            stored.setResolvedAt(created.plus(resolvedAfterHours, ChronoUnit.HOURS));
        }
        return ticketRepository.save(stored);
    }

    private JsonNode agentRow(JsonNode rows) {
        for (JsonNode row : rows) {
            if (row.get("agent_id").asLong() == agent.getId()) {
                return row;
            }
        }
        throw new AssertionError(agent.getId() + " not in " + rows);
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.with(user(admin.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
