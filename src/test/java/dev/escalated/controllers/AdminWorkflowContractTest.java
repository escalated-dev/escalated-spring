package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.Workflow;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.repositories.WorkflowLogRepository;
import dev.escalated.repositories.WorkflowRepository;
import dev.escalated.services.TicketService;
import dev.escalated.services.WorkflowRunnerService;
import hostapp.HostApplication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The workflow admin surface against escalated-developer-context
 * {@code domain-model/workflow-admin-contract.md}: the builder's request body,
 * over HTTP, saves a workflow that then runs.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class AdminWorkflowContractTest {

    private static final String BASE = "/escalated/api/admin/workflows";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkflowRepository workflows;
    @Autowired private WorkflowLogRepository workflowLogs;
    @Autowired private AgentProfileRepository agents;
    @Autowired private DepartmentRepository departments;
    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository tickets;
    @Autowired private TagRepository tags;
    @Autowired private WorkflowRunnerService runner;

    @Autowired
    @Qualifier(EscalatedTransactionManagers.ESCALATED)
    private PlatformTransactionManager transactionManager;

    private String unique;
    private AgentProfile admin;
    private Department billing;
    private final List<Long> created = new ArrayList<>();

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        admin = staff(true);
        billing = new Department();
        billing.setName("Billing " + unique);
        billing = departments.save(billing);
    }

    @AfterEach
    void removeWorkflows() {
        await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(5)).until(() -> true);
        inTransaction(() -> {
            workflowLogs.findAll().stream()
                    .filter(row -> row.getWorkflow() != null && created.contains(row.getWorkflow().getId()))
                    .forEach(workflowLogs::delete);
            created.stream().filter(workflows::existsById).forEach(workflows::deleteById);
            return null;
        });
    }

    /** The example body from the contract, word for word apart from the ids. */
    private String contractBody(String name) {
        return """
                {
                  "name": "%s",
                  "description": null,
                  "trigger_event": "ticket.created",
                  "conditions": {
                    "all": [{ "field": "subject", "operator": "contains", "value": "refund %s" }]
                  },
                  "actions": [
                    { "type": "change_priority", "value": "high" },
                    { "type": "set_department", "value": "%d" }
                  ],
                  "is_active": true
                }
                """.formatted(name, unique, billing.getId());
    }

    @Test
    void theBuildersBodyIsStoredAsSent() throws Exception {
        JsonNode body = createFrom(contractBody("Route refunds to billing " + unique));

        assertThat(body.get("trigger_event").asText()).isEqualTo("ticket.created");
        assertThat(body.get("conditions").get("all").get(0).get("field").asText()).isEqualTo("subject");
        assertThat(body.get("actions")).hasSize(2);
        assertThat(body.get("actions").get(0).get("type").asText()).isEqualTo("change_priority");
        assertThat(body.get("is_active").asBoolean()).isTrue();

        Workflow stored = workflows.findById(body.get("id").asLong()).orElseThrow();
        assertThat(stored.getTriggerEvent()).isEqualTo("ticket.created");
        assertThat(JSON.readTree(stored.getConditions()))
                .isEqualTo(JSON.readTree("{\"all\":[{\"field\":\"subject\",\"operator\":\"contains\",\"value\":\"refund "
                        + unique + "\"}]}"));
        assertThat(JSON.readTree(stored.getActions()).get(1).get("value").asText())
                .isEqualTo(String.valueOf(billing.getId()));
    }

    @Test
    void aWorkflowSavedFromTheBuilderRunsOnItsTrigger() throws Exception {
        createFrom(contractBody("Route refunds " + unique));

        Ticket ticket = ticketService.create("Please refund " + unique + " now", "Body", "Customer",
                "customer-" + unique + "@example.com", TicketPriority.LOW, null);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Ticket fresh = tickets.findById(ticket.getId()).orElseThrow();
            assertThat(fresh.getPriority()).isEqualTo(TicketPriority.HIGH);
            assertThat(inTransaction(() -> tickets.findById(ticket.getId()).orElseThrow().getDepartment().getId()))
                    .isEqualTo(billing.getId());
        });
    }

    @Test
    void theFormOptionsListWhatTheBackendCanDo() throws Exception {
        mockMvc.perform(get(BASE + "/options").with(user(admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trigger_events[*].value", hasItem("ticket.created")))
                .andExpect(jsonPath("$.trigger_events[*].value", hasItem("ticket.tag_added")))
                .andExpect(jsonPath("$.trigger_events[0].label").isString())
                .andExpect(jsonPath("$.action_types[*].value", hasItem("insert_canned_reply")))
                .andExpect(jsonPath("$.action_types[*].value", hasItem("set_department")))
                .andExpect(jsonPath("$.operators", hasItem("is_not_empty")))
                .andExpect(jsonPath("$.condition_fields", hasItem("hours_since_created")));

        JsonNode options = JSON.readTree(mockMvc.perform(get(BASE + "/options").with(user(admin.getEmail())))
                .andReturn().getResponse().getContentAsString());
        List<String> actions = new ArrayList<>();
        options.get("action_types").forEach(node -> actions.add(node.get("value").asText()));
        // The executor cannot send webhooks or notifications; do not offer them.
        assertThat(actions).doesNotContain("send_webhook", "send_notification");
    }

    @Test
    void invalidBodiesAnswer422WithFieldErrors() throws Exception {
        mockMvc.perform(post(BASE).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger_event\":\"ticket.nonsense\",\"actions\":[]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.errors", hasKey("name")))
                .andExpect(jsonPath("$.errors", hasKey("trigger_event")))
                .andExpect(jsonPath("$.errors", hasKey("actions")));

        mockMvc.perform(post(BASE).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"trigger_event\":\"ticket.created\","
                                + "\"conditions\":{\"all\":[],\"any\":[]},"
                                + "\"actions\":[{\"type\":\"launch_rocket\",\"value\":\"1\"}]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("conditions")))
                .andExpect(jsonPath("$.errors", hasKey("actions.0.type")));
    }

    @Test
    void omittedConditionsMatchEveryTicket() throws Exception {
        JsonNode body = createFrom("{\"name\":\"Everything " + unique + "\",\"trigger_event\":\"ticket.created\","
                + "\"actions\":[{\"type\":\"change_priority\",\"value\":\"urgent\"}]}");

        assertThat(body.get("conditions").get("all")).isEmpty();
    }

    @Test
    void updateToggleReorderAndDelete() throws Exception {
        long first = createFrom(contractBody("First " + unique)).get("id").asLong();
        long second = createFrom(contractBody("Second " + unique)).get("id").asLong();

        // A legacy trigger name is stored as its canonical name.
        mockMvc.perform(put(BASE + "/{id}", first).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed " + unique + "\",\"trigger_event\":\"ticket.tagged\","
                                + "\"conditions\":{\"any\":[{\"field\":\"priority\",\"operator\":\"equals\",\"value\":\"high\"}]},"
                                + "\"actions\":[{\"type\":\"add_note\",\"value\":\"Tagged {{ticket_number}}\"}],"
                                + "\"is_active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed " + unique))
                .andExpect(jsonPath("$.trigger_event").value("ticket.tag_added"))
                .andExpect(jsonPath("$.conditions.any[0].field").value("priority"));

        mockMvc.perform(post(BASE + "/{id}/toggle", first).with(user(admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_active").value(false));
        assertThat(workflows.findById(first).orElseThrow().isActive()).isFalse();

        mockMvc.perform(post(BASE + "/reorder").with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workflow_ids\":[" + second + "," + first + "]}"))
                .andExpect(status().isOk());
        assertThat(workflows.findById(second).orElseThrow().getPosition())
                .isLessThan(workflows.findById(first).orElseThrow().getPosition());

        Ticket ticket = ticketService.create("Logged " + unique, "Body", "Customer",
                "customer-" + unique + "@example.com", TicketPriority.LOW, null);
        runner.runForEvent("ticket.created", ticket.getId());
        mockMvc.perform(get(BASE + "/{id}/logs", second).with(user(admin.getEmail())))
                .andExpect(status().isOk());

        mockMvc.perform(delete(BASE + "/{id}", second).with(user(admin.getEmail())))
                .andExpect(status().isNoContent());
        assertThat(workflows.existsById(second)).isFalse();
        mockMvc.perform(get(BASE + "/{id}", second).with(user(admin.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void theIndexListsWorkflowObjects() throws Exception {
        long id = createFrom(contractBody("Listed " + unique)).get("id").asLong();

        JsonNode list = JSON.readTree(mockMvc.perform(get(BASE).with(user(admin.getEmail())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode mine = null;
        for (JsonNode node : list) {
            if (node.get("id").asLong() == id) {
                mine = node;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("conditions").isObject()).isTrue();
        assertThat(mine.get("actions").isArray()).isTrue();
        assertThat(mine.has("position")).isTrue();
        assertThat(mine.has("workflow_logs")).isFalse();
        assertThat(mine.has("workflowLogs")).isFalse();
    }

    @Test
    void agentsWhoAreNotAdminsCannotWrite() throws Exception {
        AgentProfile agent = staff(false);

        mockMvc.perform(post(BASE).with(user(agent.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractBody("Nope " + unique)))
                .andExpect(status().isForbidden());
    }

    @Test
    void contractConditionFieldsAndStoredShapesEvaluate() throws Exception {
        Tag vip = new Tag();
        vip.setName("vip-" + unique);
        tags.save(vip);
        createFrom("{\"name\":\"Tags " + unique + "\",\"trigger_event\":\"ticket.updated\","
                + "\"conditions\":{\"all\":[{\"field\":\"tags\",\"operator\":\"contains\",\"value\":\"vip-" + unique
                + "\"},{\"field\":\"hours_since_created\",\"operator\":\"less_than\",\"value\":\"1\"},"
                + "{\"field\":\"channel\",\"operator\":\"is_not_empty\",\"value\":\"\"}]},"
                + "\"actions\":[{\"type\":\"change_priority\",\"value\":\"urgent\"}]}");
        // Stored by an older release as a flat list (read as `all`).
        Workflow flat = new Workflow();
        flat.setName("Flat " + unique);
        flat.setTriggerEvent("ticket.updated");
        flat.setConditions("[{\"field\":\"subject\",\"operator\":\"contains\",\"value\":\"Flat " + unique + "\"}]");
        flat.setActions("[{\"type\":\"change_status\",\"value\":\"pending\"}]");
        created.add(workflows.save(flat).getId());

        Ticket ticket = ticketService.create("Flat " + unique, "Body", "Customer",
                "customer-" + unique + "@example.com", TicketPriority.LOW, null);
        ticketService.addTag(ticket.getId(), vip.getName());
        inTransaction(() -> {
            runner.runForEvent("ticket.updated", ticket.getId());
            return null;
        });

        Ticket fresh = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(fresh.getPriority()).isEqualTo(TicketPriority.URGENT);
        assertThat(fresh.getStatus().name()).isEqualTo("PENDING");
    }

    private JsonNode createFrom(String body) throws Exception {
        String response = mockMvc.perform(post(BASE).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = JSON.readTree(response);
        created.add(node.get("id").asLong());
        return node;
    }

    private AgentProfile staff(boolean isAdmin) {
        AgentProfile profile = new AgentProfile();
        profile.setName("Staff " + unique);
        profile.setEmail("staff-" + UUID.randomUUID() + "@example.com");
        profile.setAgent(true);
        profile.setAdmin(isAdmin);
        return agents.save(profile);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
