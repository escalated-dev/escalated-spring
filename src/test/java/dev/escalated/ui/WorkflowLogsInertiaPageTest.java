package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.Workflow;
import dev.escalated.models.WorkflowLog;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.WorkflowLogRepository;
import dev.escalated.repositories.WorkflowRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.time.Instant;
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

/** {@code Escalated/Admin/Workflows/Logs} over the Inertia protocol. */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class WorkflowLogsInertiaPageTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private WorkflowRepository workflows;
    @Autowired private WorkflowLogRepository logs;
    @Autowired private TicketService tickets;

    private String unique;
    private AgentProfile admin;
    private Workflow refunds;
    private Workflow other;
    private Ticket ticket;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin = agents.save(admin);

        refunds = workflow("Refunds " + unique);
        other = workflow("Other " + unique);
        ticket = tickets.create("Logged " + unique, "b", "R", "r-" + unique + "@example.com", TicketPriority.LOW, null);
    }

    @Test
    void theLogsPageListsExecutionsAsTheSharedPageReadsThem() throws Exception {
        WorkflowLog ran = log(refunds, "[{\"type\":\"change_priority\",\"value\":\"high\"},{\"type\":\"add_tag\",\"value\":\"x\"}]",
                null);
        WorkflowLog failed = log(other, "[]", "Boom " + unique);

        JsonNode page = inertia(get("/escalated/admin/workflows/logs"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Workflows/Logs");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("logs", "workflows");
        JsonNode row = find(props.get("logs"), ran.getId());
        assertThat(fieldNames(row)).contains("id", "event", "workflow_name", "ticket_reference", "matched",
                "actions_executed", "action_details", "status", "duration_ms", "created_at");
        assertThat(row.get("workflow_name").asText()).isEqualTo("Refunds " + unique);
        assertThat(row.get("ticket_reference").asText()).isEqualTo(ticket.getTicketNumber());
        assertThat(row.get("event").asText()).isEqualTo("ticket.created");
        assertThat(row.get("actions_executed").asInt()).isEqualTo(2);
        assertThat(row.get("action_details").size()).isEqualTo(2);
        assertThat(row.get("status").asText()).isEqualTo("success");
        assertThat(row.get("duration_ms").asLong()).isEqualTo(250);
        assertThat(find(props.get("logs"), failed.getId()).get("status").asText()).isEqualTo("failed");
        assertThat(fieldNames(find(props.get("workflows"), refunds.getId()))).containsExactly("id", "name");

        JsonNode filtered = inertia(get("/escalated/admin/workflows/logs").param("workflow", refunds.getId().toString()))
                .get("props").get("logs");
        assertThat(filtered.size()).isEqualTo(1);
        assertThat(filtered.get(0).get("id").asLong()).isEqualTo(ran.getId());
    }

    @Test
    void thePerWorkflowUrlOpensTheFilteredLogsPage() throws Exception {
        mvc.perform(get("/escalated/admin/workflows/" + refunds.getId() + "/logs").with(user(admin.getEmail())))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/escalated/admin/workflows/logs?workflow=" + refunds.getId()));
    }

    @Test
    void anAgentWhoIsNotAnAdminIsRefused() throws Exception {
        AgentProfile agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agents.save(agent);
        mvc.perform(get("/escalated/admin/workflows/logs").with(user(agent.getEmail()))).andExpect(status().isForbidden());
    }

    private Workflow workflow(String name) {
        Workflow workflow = new Workflow();
        workflow.setName(name);
        workflow.setTriggerEvent("ticket.created");
        workflow.setConditions("{\"all\":[]}");
        workflow.setActions("[]");
        // Inactive, so creating the test ticket runs neither and only the seeded logs exist.
        workflow.setActive(false);
        return workflows.save(workflow);
    }

    private WorkflowLog log(Workflow workflow, String actions, String error) {
        WorkflowLog log = new WorkflowLog();
        log.setWorkflow(workflow);
        log.setTicket(ticket);
        log.setTriggerEvent("ticket.created");
        log.setConditionsMatched(true);
        log.setActionsExecutedJson(actions);
        log.setErrorMessage(error);
        Instant started = Instant.now();
        log.setStartedAt(started);
        log.setCompletedAt(started.plusMillis(250));
        return logs.save(log);
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.with(user(admin.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode find(JsonNode array, Long id) {
        for (JsonNode item : array) {
            if (item.get("id").asLong() == id) {
                return item;
            }
        }
        throw new AssertionError(id + " not in " + array);
    }
}
