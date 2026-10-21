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
import dev.escalated.models.CannedResponse;
import dev.escalated.models.Department;
import dev.escalated.models.Macro;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.models.Webhook;
import dev.escalated.models.WebhookDelivery;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.CannedResponseRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.MacroRepository;
import dev.escalated.repositories.WebhookDeliveryRepository;
import dev.escalated.repositories.WebhookRepository;
import dev.escalated.services.MacroService;
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

/** The admin configuration pages over the Inertia protocol: macros, canned responses and webhooks. */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class AdminConfigInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** RFC 6761: never resolves, so it is accepted at save time and fails at send time. */
    private static final String HOOK_HOST = "https://hooks.invalid";

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private MacroRepository macros;
    @Autowired private CannedResponseRepository cannedResponses;
    @Autowired private WebhookRepository webhooks;
    @Autowired private WebhookDeliveryRepository deliveries;
    @Autowired private DepartmentRepository departments;
    @Autowired private TicketService tickets;
    @Autowired private MacroService macroService;

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
        for (String path : new String[] {"/escalated/admin/macros", "/escalated/admin/canned-responses",
                "/escalated/admin/webhooks", "/escalated/admin/webhooks/create"}) {
            mvc.perform(get(path).with(user(agent.getEmail()))).andExpect(status().isForbidden());
        }
    }

    @Test
    void macrosAreListedCreatedUpdatedAndDeleted() throws Exception {
        String body = """
                {"name":"Triage %s","description":"First pass","is_shared":true,
                 "actions":[{"type":"status","value":"in_progress","order":0},
                            {"type":"note","value":"Triaged","order":1}]}
                """.formatted(unique);
        write(post("/escalated/admin/macros"), body)
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/macros"));
        Macro created = macros.findAll().stream().filter(m -> m.getName().equals("Triage " + unique))
                .findFirst().orElseThrow();
        assertThat(created.getCreatedByAgentId()).isEqualTo(admin.getId());

        JsonNode page = inertia(get("/escalated/admin/macros"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Macros/Index");
        JsonNode row = find(page.get("props").get("macros"), created.getId());
        assertThat(fieldNames(row)).contains("id", "name", "description", "actions", "is_shared", "order");
        assertThat(row.get("is_shared").asBoolean()).isTrue();
        assertThat(fieldNames(row.get("actions").get(0))).contains("type", "value", "order");
        assertThat(row.get("actions").get(1).get("type").asText()).isEqualTo("note");

        write(put("/escalated/admin/macros/" + created.getId()),
                body.replace("Triage", "Sorted").replace("\"is_shared\":true", "\"is_shared\":false"))
                .andExpect(status().isSeeOther());
        Macro updated = macros.findById(created.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Sorted " + unique);
        assertThat(updated.isShared()).isFalse();

        write(delete("/escalated/admin/macros/" + created.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(macros.existsById(created.getId())).isFalse();
    }

    @Test
    void aMacroSavedFromThePageAppliesToATicket() throws Exception {
        Department department = new Department();
        department.setName("Escalations " + unique);
        department = departments.save(department);
        String body = """
                {"name":"Escalate %s","is_shared":true,"actions":[
                 {"type":"status","value":"waiting_on_customer"},{"type":"priority","value":"urgent"},
                 {"type":"assign","value":"%s"},{"type":"tags","value":"vip-%s, hot-%s"},
                 {"type":"department","value":"Escalations %s"},{"type":"reply","value":"On it"},
                 {"type":"note","value":"Escalated"}]}
                """.formatted(unique, agent.getEmail(), unique, unique, unique);
        write(post("/escalated/admin/macros"), body).andExpect(status().isSeeOther());
        Macro macro = macros.findAll().stream().filter(m -> m.getName().equals("Escalate " + unique))
                .findFirst().orElseThrow();
        Ticket ticket = tickets.create("Macro " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.LOW, null);

        macroService.applyMacro(macro.getId(), ticket.getId(), admin.getEmail());

        Ticket applied = tickets.findById(ticket.getId());
        assertThat(applied.getStatus()).isEqualTo(TicketStatus.PENDING);
        assertThat(applied.getPriority()).isEqualTo(TicketPriority.URGENT);
        assertThat(applied.getAssignedAgent().getId()).isEqualTo(agent.getId());
        assertThat(applied.getDepartment().getId()).isEqualTo(department.getId());
        JsonNode shown = inertia(get("/escalated/admin/tickets/" + applied.getTicketNumber())).get("props").get("ticket");
        assertThat(shown.get("tags").toString()).contains("vip-" + unique, "hot-" + unique);
        assertThat(shown.get("replies").toString()).contains("On it", "Escalated");
    }

    @Test
    void anInvalidMacroGoesBackWithErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/macros").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/macros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad " + unique + "\",\"actions\":[{\"type\":\"explode\",\"value\":\"x\"}]}"))
                .andExpect(status().isSeeOther());
        JsonNode errors = inertia(get("/escalated/admin/macros").session(session)).get("props").get("errors");
        assertThat(errors.has("actions.0.type")).isTrue();
        assertThat(macros.findAll().stream().noneMatch(m -> m.getName().equals("Bad " + unique))).isTrue();
    }

    @Test
    void cannedResponsesAreListedCreatedUpdatedAndDeleted() throws Exception {
        write(post("/escalated/admin/canned-responses"),
                "{\"title\":\"Hello " + unique + "\",\"body\":\"Thanks for writing.\",\"category\":\"General\","
                        + "\"is_shared\":true}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/canned-responses"));
        CannedResponse created = cannedResponses.findAll().stream()
                .filter(r -> r.getTitle().equals("Hello " + unique)).findFirst().orElseThrow();

        JsonNode page = inertia(get("/escalated/admin/canned-responses"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/CannedResponses/Index");
        JsonNode row = find(page.get("props").get("responses"), created.getId());
        assertThat(fieldNames(row)).contains("id", "title", "body", "category", "is_shared", "creator");
        assertThat(row.get("body").asText()).isEqualTo("Thanks for writing.");
        assertThat(row.get("creator").get("name").asText()).isEqualTo(admin.getName());
        assertThat(fieldNames(row.get("creator"))).containsExactly("id", "name");

        write(put("/escalated/admin/canned-responses/" + created.getId()),
                "{\"title\":\"Hi " + unique + "\",\"body\":\"Thanks.\",\"category\":null,\"is_shared\":false}")
                .andExpect(status().isSeeOther());
        CannedResponse updated = cannedResponses.findById(created.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Hi " + unique);
        assertThat(updated.isShared()).isFalse();

        write(delete("/escalated/admin/canned-responses/" + created.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(cannedResponses.existsById(created.getId())).isFalse();
    }

    @Test
    void anEmptyCannedResponseGoesBackWithErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/canned-responses").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/canned-responses")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"body\":\"\"}"))
                .andExpect(status().isSeeOther());
        JsonNode errors = inertia(get("/escalated/admin/canned-responses").session(session)).get("props").get("errors");
        assertThat(errors.get("title").asText()).isEqualTo("The title field is required.");
    }

    @Test
    void webhooksAreListedCreatedEditedAndDeletedWithoutTheirSecret() throws Exception {
        String url = HOOK_HOST + "/" + unique;
        write(post("/escalated/admin/webhooks"),
                "{\"url\":\"" + url + "\",\"events\":[\"ticket.created\",\"ticket.tagged\"],"
                        + "\"secret\":\"whsec-" + unique + "\",\"active\":true}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/webhooks"));
        Webhook created = webhooks.findAll().stream().filter(w -> w.getUrl().equals(url)).findFirst().orElseThrow();
        assertThat(created.getSecret()).isEqualTo("whsec-" + unique);
        assertThat(created.getEvents()).contains("ticket.tag_added").doesNotContain("ticket.tagged");

        JsonNode index = inertia(get("/escalated/admin/webhooks"));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/Webhooks/Index");
        JsonNode row = find(index.get("props").get("webhooks"), created.getId());
        assertThat(fieldNames(row)).contains("id", "url", "events", "active", "deliveries");
        assertThat(row.get("events").size()).isEqualTo(2);
        assertThat(index.toString()).doesNotContain("whsec-" + unique);

        JsonNode create = inertia(get("/escalated/admin/webhooks/create"));
        assertThat(create.get("component").asText()).isEqualTo("Escalated/Admin/Webhooks/Form");
        assertThat(create.get("props").get("availableEvents").toString()).contains("ticket.created", "reply.created");

        JsonNode edit = inertia(get("/escalated/admin/webhooks/" + created.getId() + "/edit"));
        assertThat(fieldNames(edit.get("props").get("webhook"))).contains("id", "url", "events", "active");
        assertThat(edit.toString()).doesNotContain("whsec-" + unique);

        // A blank secret keeps the stored one.
        write(put("/escalated/admin/webhooks/" + created.getId()),
                "{\"url\":\"" + url + "/v2\",\"events\":[\"ticket.closed\"],\"secret\":\"\",\"active\":false}")
                .andExpect(status().isSeeOther());
        Webhook updated = webhooks.findById(created.getId()).orElseThrow();
        assertThat(updated.getUrl()).isEqualTo(url + "/v2");
        assertThat(updated.getEvents()).isEqualTo("ticket.closed");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getSecret()).isEqualTo("whsec-" + unique);

        write(delete("/escalated/admin/webhooks/" + created.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(webhooks.existsById(created.getId())).isFalse();
    }

    @Test
    void aWebhookToAPrivateAddressGoesBackWithAnError() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/webhooks").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/webhooks/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://127.0.0.1/" + unique + "\",\"events\":[\"ticket.created\"]}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/webhooks/create"));
        JsonNode errors = inertia(get("/escalated/admin/webhooks/create").session(session)).get("props").get("errors");
        assertThat(errors.get("url").asText()).contains("public address");
        assertThat(webhooks.findAll().stream().noneMatch(w -> w.getUrl().contains(unique))).isTrue();
    }

    @Test
    void theDeliveryLogPaginatesAndADeliveryIsRetried() throws Exception {
        Webhook webhook = new Webhook();
        webhook.setUrl(HOOK_HOST + "/" + unique);
        webhook.setEvents("ticket.created");
        webhook = webhooks.save(webhook);
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhook(webhook);
        delivery.setEventType("ticket.created");
        delivery.setPayload("{\"event\":\"ticket.created\",\"entity_id\":7}");
        delivery.setResponseStatus(500);
        delivery.setStatus("failed");
        delivery.setAttemptCount(1);
        delivery = deliveries.save(delivery);

        JsonNode page = inertia(get("/escalated/admin/webhooks/" + webhook.getId() + "/deliveries"));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Webhooks/DeliveryLog");
        JsonNode props = page.get("props");
        assertThat(props.get("webhook").get("url").asText()).isEqualTo(HOOK_HOST + "/" + unique);
        assertThat(fieldNames(props.get("deliveries"))).contains("data", "current_page", "last_page", "links");
        JsonNode row = props.get("deliveries").get("data").get(0);
        assertThat(fieldNames(row)).contains("id", "event", "payload", "response_code", "attempts", "created_at");
        assertThat(row.get("payload").get("entity_id").asInt()).isEqualTo(7);
        assertThat(row.get("response_code").asInt()).isEqualTo(500);

        JsonNode index = inertia(get("/escalated/admin/webhooks"));
        assertThat(find(index.get("props").get("webhooks"), webhook.getId()).get("deliveries").get(0)
                .get("response_code").asInt()).isEqualTo(500);

        write(post("/escalated/admin/webhooks/deliveries/" + delivery.getId() + "/retry"), "{}")
                .andExpect(status().isSeeOther());
        WebhookDelivery retried = deliveries.findById(delivery.getId()).orElseThrow();
        assertThat(retried.getAttemptCount()).isEqualTo(2);
        assertThat(retried.getStatus()).isEqualTo("failed");
        assertThat(retried.getErrorMessage()).isNotBlank();

        write(post("/escalated/admin/webhooks/deliveries/999999999/retry"), "{}").andExpect(status().isNotFound());
    }

    @Test
    void theRouteTableNamesWhatThePagesCall() throws Exception {
        String html = mvc.perform(get("/escalated/admin/macros").with(user(admin.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("\"escalated.admin.macros.update\":\"/escalated/admin/macros/{id}\"",
                "\"escalated.admin.canned-responses.destroy\":\"/escalated/admin/canned-responses/{id}\"",
                "\"escalated.admin.webhooks.edit\":\"/escalated/admin/webhooks/{webhook}/edit\"",
                "\"escalated.admin.webhooks.deliveries\":\"/escalated/admin/webhooks/{webhook}/deliveries\"",
                "\"escalated.admin.webhooks.retry\":\"/escalated/admin/webhooks/deliveries/{delivery}/retry\"");
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
