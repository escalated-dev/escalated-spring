package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Automation;
import dev.escalated.models.Department;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.Workflow;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AutomationRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.WorkflowRepository;
import dev.escalated.services.SettingsService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The admin pages over the Inertia protocol: components, the props the pages read, and every write. */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class AdminInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private WorkflowRepository workflows;
    @Autowired private AutomationRepository automations;
    @Autowired private DepartmentRepository departments;
    @Autowired private TagRepository tags;
    @Autowired private SettingsService settings;
    @Autowired private TicketService tickets;

    private String unique;
    private AgentProfile admin;
    private AgentProfile agent;

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
    }

    @Test
    void anAgentWhoIsNotAnAdminIsRefused() throws Exception {
        for (String path : new String[] {"/escalated/admin/settings", "/escalated/admin/workflows",
                "/escalated/admin/tags", "/escalated/admin/departments", "/escalated/admin/automations"}) {
            mvc.perform(get(path).with(user(agent.getEmail()))).andExpect(status().isForbidden());
        }
    }

    @Test
    void settingsListOnlyWhatThisPortStoresAndSaveIt() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/settings"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Settings");
        JsonNode props = page.get("props");
        assertThat(props.get("supported_settings").toString()).contains("widget_color", "show_powered_by",
                "allow_customer_close", "widget_allowed_origins");
        assertThat(fieldNames(props.get("settings")))
                .containsExactlyInAnyOrderElementsOf(AdminUiPages.SUPPORTED_SETTINGS);

        // As Inertia's client does it: the XSRF-TOKEN cookie echoed back in X-XSRF-TOKEN.
        jakarta.servlet.http.Cookie xsrf = mvc.perform(get("/escalated/admin/settings").with(user(admin.getEmail())))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        mvc.perform(post("/escalated/admin/settings").with(user(admin.getEmail())).cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()).header("X-Inertia", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"widget_color\":\"#123ABC\",\"show_powered_by\":false,"
                                + "\"widget_position\":\"bottom-left\",\"ticket_reference_prefix\":\"IGNORED\"}"))
                .andExpect(status().isSeeOther());
        assertThat(settings.get("widget_color")).contains("#123ABC");
        assertThat(settings.get("show_powered_by")).contains("0");
        assertThat(settings.get("ticket_reference_prefix")).isEmpty();
        assertThat(inertia(get("/escalated/admin/settings")).get("props").get("escalated")
                .get("show_powered_by").asBoolean()).isFalse();
        settings.set("show_powered_by", "1", "general");
    }

    @Test
    void anInvalidSettingIsAnErrorAndNothingIsSaved() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/settings").session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost/escalated/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"widget_color\":\"red\",\"widget_greeting\":\"Hello " + unique + "\"}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/settings"));

        JsonNode errors = inertia(get("/escalated/admin/settings").session(session)).get("props").get("errors");
        assertThat(errors.has("widget_color")).isTrue();
        assertThat(settings.get("widget_greeting")).isNotEqualTo(java.util.Optional.of("Hello " + unique));
    }

    @Test
    void workflowsAreListedCreatedEditedToggledAndDeleted() throws Exception {
        String body = """
                {"name":"Refunds %s","description":null,"trigger_event":"ticket.created",
                 "conditions":{"all":[{"field":"subject","operator":"contains","value":"refund"}]},
                 "actions":[{"type":"change_priority","value":"high"}],"is_active":true}
                """.formatted(unique);
        write(post("/escalated/admin/workflows"), body)
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/workflows"));
        Workflow created = workflows.findAll().stream().filter(w -> w.getName().equals("Refunds " + unique))
                .findFirst().orElseThrow();

        JsonNode index = inertia(get("/escalated/admin/workflows"));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/Workflows/Index");
        JsonNode row = find(index.get("props").get("workflows"), created.getId());
        assertThat(fieldNames(row)).contains("id", "name", "trigger_event", "conditions", "actions", "is_active");

        JsonNode form = inertia(get("/escalated/admin/workflows/create"));
        assertThat(form.get("component").asText()).isEqualTo("Escalated/Admin/Workflows/Form");
        assertThat(form.get("props").get("workflow").isNull()).isTrue();
        assertThat(fieldNames(form.get("props"))).contains("trigger_events", "action_types", "operators");
        assertThat(form.get("props").get("trigger_events").get(0).has("value")).isTrue();

        JsonNode edit = inertia(get("/escalated/admin/workflows/" + created.getId()));
        assertThat(edit.get("props").get("workflow").get("name").asText()).isEqualTo("Refunds " + unique);

        write(put("/escalated/admin/workflows/" + created.getId()), body.replace("Refunds", "Returns"))
                .andExpect(status().isSeeOther());
        assertThat(workflows.findById(created.getId()).orElseThrow().getName()).isEqualTo("Returns " + unique);

        write(post("/escalated/admin/workflows/" + created.getId() + "/toggle"), "{}").andExpect(status().isSeeOther());
        assertThat(workflows.findById(created.getId()).orElseThrow().isActive()).isFalse();

        write(post("/escalated/admin/workflows/reorder"), "{\"workflow_ids\":[" + created.getId() + "]}")
                .andExpect(status().isSeeOther());
        assertThat(workflows.findById(created.getId()).orElseThrow().getPosition()).isZero();

        write(delete("/escalated/admin/workflows/" + created.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(workflows.existsById(created.getId())).isFalse();
    }

    @Test
    void anInvalidWorkflowGoesBackWithErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/workflows").session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost/escalated/admin/workflows/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"trigger_event\":\"nope\",\"actions\":[]}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/workflows/create"));
        JsonNode errors = inertia(get("/escalated/admin/workflows/create").session(session)).get("props").get("errors");
        assertThat(errors.has("name")).isTrue();
    }

    @Test
    void automationsAreListedCreatedUpdatedAndDeleted() throws Exception {
        String body = """
                {"name":"Nudge %s","conditions":[{"field":"hours_since_created","operator":">","value":24}],
                 "actions":[{"type":"add_note","value":"Still waiting"}],"active":true}
                """.formatted(unique);
        write(post("/escalated/admin/automations"), body)
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/automations"));
        Automation created = automations.findAll().stream().filter(a -> a.getName().equals("Nudge " + unique))
                .findFirst().orElseThrow();

        JsonNode index = inertia(get("/escalated/admin/automations"));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/Automations/Index");
        assertThat(fieldNames(find(index.get("props").get("automations"), created.getId())))
                .contains("id", "name", "conditions", "actions", "active");
        assertThat(inertia(get("/escalated/admin/automations/create")).get("component").asText())
                .isEqualTo("Escalated/Admin/Automations/Form");
        assertThat(inertia(get("/escalated/admin/automations/" + created.getId() + "/edit")).get("props")
                .get("automation").get("name").asText()).isEqualTo("Nudge " + unique);

        write(put("/escalated/admin/automations/" + created.getId()), body.replace("\"active\":true", "\"active\":false"))
                .andExpect(status().isSeeOther());
        assertThat(automations.findById(created.getId()).orElseThrow().isActive()).isFalse();

        write(delete("/escalated/admin/automations/" + created.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(automations.existsById(created.getId())).isFalse();
    }

    @Test
    void departmentsAreListedCreatedEditedAndDeleted() throws Exception {
        write(post("/escalated/admin/departments"),
                "{\"name\":\"Ops " + unique + "\",\"slug\":\"ops\",\"description\":\"Desk\",\"is_active\":true}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/departments"));
        Department created = departments.findByName("Ops " + unique).orElseThrow();
        Ticket ticket = tickets.create("Dept " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.LOW, created.getId());

        JsonNode index = inertia(get("/escalated/admin/departments"));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/Departments/Index");
        JsonNode row = find(index.get("props").get("departments"), created.getId());
        assertThat(fieldNames(row)).contains("id", "name", "is_active", "tickets_count", "agents_count");
        assertThat(row.get("tickets_count").asInt()).isEqualTo(1);

        assertThat(inertia(get("/escalated/admin/departments/create")).get("component").asText())
                .isEqualTo("Escalated/Admin/Departments/Form");
        JsonNode edit = inertia(get("/escalated/admin/departments/" + created.getId() + "/edit"));
        assertThat(fieldNames(edit.get("props").get("department"))).contains("id", "name", "description", "is_active");

        write(put("/escalated/admin/departments/" + created.getId()),
                "{\"name\":\"Ops " + unique + "\",\"description\":\"Desk\",\"is_active\":false}")
                .andExpect(status().isSeeOther());
        assertThat(departments.findById(created.getId()).orElseThrow().isActive()).isFalse();

        write(delete("/escalated/admin/departments/" + created.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(departments.existsById(created.getId())).isFalse();
        assertThat(tickets.findById(ticket.getId()).getDepartment()).isNull();
    }

    @Test
    void tagsAreListedCreatedRenamedAndDeleted() throws Exception {
        write(post("/escalated/admin/tags"), "{\"name\":\"urgent-" + unique + "\",\"color\":\"#06b6d4\"}")
                .andExpect(status().isSeeOther());
        Tag created = tags.findByName("urgent-" + unique).orElseThrow();
        Ticket ticket = tickets.create("Tagged " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.LOW, null);
        tickets.addTag(ticket.getId(), created.getName());

        JsonNode index = inertia(get("/escalated/admin/tags"));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/Tags/Index");
        JsonNode row = find(index.get("props").get("tags"), created.getId());
        assertThat(fieldNames(row)).containsExactly("id", "name", "color", "tickets_count");
        assertThat(row.get("tickets_count").asInt()).isEqualTo(1);

        write(post("/escalated/admin/tags"), "{\"name\":\"urgent-" + unique + "\"}").andExpect(status().isSeeOther());
        assertThat(tags.findAll().stream().filter(t -> t.getName().equals("urgent-" + unique)).count()).isEqualTo(1);

        write(put("/escalated/admin/tags/" + created.getId()), "{\"name\":\"hot-" + unique + "\",\"color\":\"#ff0000\"}")
                .andExpect(status().isSeeOther());
        assertThat(tags.findById(created.getId()).orElseThrow().getName()).isEqualTo("hot-" + unique);

        write(delete("/escalated/admin/tags/" + created.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(tags.existsById(created.getId())).isFalse();
        assertThat(inertia(get("/escalated/agent/tickets/" + ticket.getTicketNumber())).get("props").get("ticket")
                .get("tags").size()).isZero();
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.with(user(admin.getEmail())).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                .contentType(MediaType.APPLICATION_JSON).content(json));
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
