package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.TicketReadRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code escalated.agent.tickets.bulk}, the agent queue's bulk bar (TicketIndex posts to it), as
 * escalated-laravel's agent bulk route: the same {@code BulkActionController} as the admin one,
 * with {@code mark_read} / {@code mark_unread} (ADR 2026-11-05-agent-panel-data) and delete for
 * admins only.
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
class AgentBulkActionTest {

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private TicketService tickets;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketReadRepository reads;
    @Autowired private UiRoutes routes;

    private String unique;
    private AgentProfile agent;
    private Ticket first;
    private Ticket second;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("bulk-agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent = agents.save(agent);

        first = tickets.create("First " + unique, "a", "Requester", "r1-" + unique + "@example.com",
                TicketPriority.LOW, null);
        second = tickets.create("Second " + unique, "b", "Requester", "r2-" + unique + "@example.com",
                TicketPriority.LOW, null);
    }

    @Test
    void theRouteHasLaravelsName() {
        assertThat(routes.url("escalated.agent.tickets.bulk")).isEqualTo("/escalated/agent/tickets/bulk");
    }

    @Test
    void anAgentChangesTicketsInBulkAndGoesBack() throws Exception {
        bulk("{\"ticket_ids\":[" + first.getId() + "," + second.getId() + "],\"action\":\"priority\",\"value\":\"urgent\"}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/agent/tickets"));

        assertThat(tickets.findById(first.getId()).getPriority()).isEqualTo(TicketPriority.URGENT);
        assertThat(tickets.findById(second.getId()).getPriority()).isEqualTo(TicketPriority.URGENT);
    }

    @Test
    void anAgentMarksTicketsReadAndUnreadInBulk() throws Exception {
        String ids = "[" + first.getId() + "," + second.getId() + "]";
        bulk("{\"ticket_ids\":" + ids + ",\"action\":\"mark_read\"}").andExpect(status().isSeeOther());
        assertThat(reads.findByAgentIdAndTicketIdIn(agent.getId(), java.util.List.of(first.getId(), second.getId())))
                .hasSize(2)
                .allSatisfy(read -> assertThat(read.getLastReadAt()).isNotNull());

        bulk("{\"ticket_ids\":" + ids + ",\"action\":\"mark_unread\"}").andExpect(status().isSeeOther());
        assertThat(reads.findByAgentIdAndTicketIdIn(agent.getId(), java.util.List.of(first.getId(), second.getId())))
                .hasSize(2)
                .allSatisfy(read -> assertThat(read.getLastReadAt()).isNull());
    }

    @Test
    void onlyAdminsDeleteInBulk() throws Exception {
        bulk("{\"ticket_ids\":[" + first.getId() + "],\"action\":\"delete\"}").andExpect(status().isSeeOther());
        assertThat(ticketRepository.existsById(first.getId())).isTrue();
    }

    @Test
    void anUnknownActionGoesBackWithAnError() throws Exception {
        bulk("{\"ticket_ids\":[" + first.getId() + "],\"action\":\"explode\",\"value\":1}")
                .andExpect(status().isSeeOther());
        assertThat(tickets.findById(first.getId()).getPriority()).isEqualTo(TicketPriority.LOW);
    }

    private ResultActions bulk(String json) throws Exception {
        return mvc.perform(post("/escalated/agent/tickets/bulk").with(user(agent.getEmail()))
                .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                .header("Referer", "http://localhost/escalated/agent/tickets")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
