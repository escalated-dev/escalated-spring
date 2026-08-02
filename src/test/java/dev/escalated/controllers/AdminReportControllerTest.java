package dev.escalated.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.controllers.admin.AdminReportController;
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
import dev.escalated.services.AdvancedReportingService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * End-to-end reachability test for the reporting endpoints: seeds tickets and
 * ratings, then drives the real controller (backed by the real service and
 * repositories over a seeded H2 database) through MockMvc, asserting each
 * report comes back populated as JSON.
 */
@DataJpaTest
@Import({AdvancedReportingService.class, AdminReportController.class})
@TestPropertySource(
        properties = {
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.flyway.enabled=false",
        })
class AdminReportControllerTest {

    @Autowired private TicketRepository tickets;
    @Autowired private SatisfactionRatingRepository ratings;
    @Autowired private AgentProfileRepository agents;
    @Autowired private SlaPolicyRepository slaPolicies;
    @Autowired private AdminReportController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        AgentProfile alice = agent("Alice", "alice@example.com");
        AgentProfile bob = agent("Bob", "bob@example.com");
        SlaPolicy sla = slaPolicy();

        Ticket t1 = ticket("ESC-1", TicketPriority.HIGH, "email", TicketStatus.RESOLVED, alice, sla,
                2.0, 10.0, 4.0, 24.0);
        Ticket t2 = ticket("ESC-2", TicketPriority.MEDIUM, "web", TicketStatus.RESOLVED, alice, sla,
                6.0, 30.0, 4.0, 24.0);
        ticket("ESC-3", TicketPriority.LOW, "chat", TicketStatus.OPEN, bob, null,
                1.0, null, null, null);

        rating(t1, 5);
        rating(t2, 3);
    }

    @Test
    void overview_endpointReturnsPopulatedReport() throws Exception {
        mockMvc.perform(get("/escalated/api/admin/reports/overview").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period_days").value(30))
                .andExpect(jsonPath("$.total_tickets").value(3))
                .andExpect(jsonPath("$.resolved_tickets").value(2))
                .andExpect(jsonPath("$.by_priority").isNotEmpty())
                .andExpect(jsonPath("$.by_channel").isNotEmpty());
    }

    @Test
    void slaEndpoint_returnsBreachCounts() throws Exception {
        mockMvc.perform(get("/escalated/api/admin/reports/sla").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_with_sla").value(2))
                .andExpect(jsonPath("$.first_response_breaches").value(1))
                .andExpect(jsonPath("$.by_priority").isArray());
    }

    @Test
    void firstResponseTimeEndpoint_returnsDistribution() throws Exception {
        mockMvc.perform(get("/escalated/api/admin/reports/first-response-time").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_measured").value(3))
                .andExpect(jsonPath("$.distribution").isArray())
                .andExpect(jsonPath("$.percentiles.p50").exists());
    }

    @Test
    void resolutionTimeEndpoint_returnsDistribution() throws Exception {
        mockMvc.perform(get("/escalated/api/admin/reports/resolution-time").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_measured").value(2));
    }

    @Test
    void csatEndpoint_returnsRatings() throws Exception {
        mockMvc.perform(get("/escalated/api/admin/reports/csat").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_ratings").value(2))
                .andExpect(jsonPath("$.average").value(4.0));
    }

    @Test
    void volumeEndpoint_returnsSeries() throws Exception {
        mockMvc.perform(get("/escalated/api/admin/reports/volume").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_tickets").value(3))
                .andExpect(jsonPath("$.series").isNotEmpty());
    }

    @Test
    void agentPerformanceEndpoint_returnsRankedAgents() throws Exception {
        mockMvc.perform(get("/escalated/api/admin/reports/agent-performance").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agents").isArray())
                .andExpect(jsonPath("$.agents[0].rank").value(1));
    }

    @Test
    void periodComparisonEndpoint_returnsChanges() throws Exception {
        mockMvc.perform(get("/escalated/api/admin/reports/period-comparison").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.total_created").value(3.0))
                .andExpect(jsonPath("$.changes").exists());
    }

    @Test
    void defaultsToThirtyDaysWhenParamOmitted() throws Exception {
        mockMvc.perform(get("/escalated/api/admin/reports/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period_days").value(30));
    }

    // ── seeding helpers ──────────────────────────────────────────────────────

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
            ticket.setFirstRespondedAt(created.plusSeconds((long) (frtHours * 3600)));
        }
        if (resolutionHours != null) {
            ticket.setResolvedAt(created.plusSeconds((long) (resolutionHours * 3600)));
        }
        if (frtDueHours != null) {
            ticket.setSlaFirstResponseDueAt(created.plusSeconds((long) (frtDueHours * 3600)));
        }
        if (resolutionDueHours != null) {
            ticket.setSlaDueAt(created.plusSeconds((long) (resolutionDueHours * 3600)));
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
}
