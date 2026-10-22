package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.CustomFieldRepository;
import dev.escalated.repositories.TicketRepository;
import hostapp.HostApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Ticket custom field values: the {@code custom_fields} object ticket writes take (keyed by slug),
 * validated before the ticket is written, and the {@code custom_fields} list ticket reads carry.
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
class TicketCustomFieldValuesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private CustomFieldRepository customFields;
    @Autowired private TicketRepository tickets;

    private String unique;
    private AgentProfile admin;
    private String customer;
    private final List<Long> created = new ArrayList<>();

    private String order;
    private String count;
    private String due;
    private String plan;
    private String tags;
    private String vip;
    private String notes;
    private String retired;
    private String userField;

    @BeforeEach
    void seed() throws Exception {
        unique = UUID.randomUUID().toString().substring(0, 8);
        customer = "customer-" + unique + "@example.com";
        admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin = agents.save(admin);

        order = field("{\"name\":\"Order %s\",\"slug\":\"order_%s\",\"type\":\"text\",\"required\":true,\"position\":1}");
        count = field("{\"name\":\"Count %s\",\"slug\":\"count_%s\",\"type\":\"number\",\"position\":2}");
        due = field("{\"name\":\"Due %s\",\"slug\":\"due_%s\",\"type\":\"date\",\"position\":3}");
        plan = field("{\"name\":\"Plan %s\",\"slug\":\"plan_%s\",\"type\":\"select\",\"options\":[\"free\",\"pro\"],"
                + "\"position\":4}");
        tags = field("{\"name\":\"Tags %s\",\"slug\":\"tags_%s\",\"type\":\"multi_select\","
                + "\"options\":[\"a\",\"b\",\"c\"],\"position\":5}");
        vip = field("{\"name\":\"Vip %s\",\"slug\":\"vip_%s\",\"type\":\"checkbox\",\"position\":6}");
        notes = field("{\"name\":\"Notes %s\",\"slug\":\"notes_%s\",\"type\":\"textarea\",\"position\":7,"
                + "\"placeholder\":\"Anything else\",\"description\":\"Free text\"}");
        retired = field("{\"name\":\"Retired %s\",\"slug\":\"retired_%s\",\"type\":\"text\",\"active\":false}");
        userField = field("{\"name\":\"Shoe %s\",\"slug\":\"shoe_%s\",\"type\":\"text\",\"context\":\"user\"}");
    }

    @AfterEach
    void forgetFields() {
        created.forEach(id -> customFields.findById(id).ifPresent(customFields::delete));
    }

    @Test
    void aTicketIsCreatedWithItsValuesAndShowsThem() throws Exception {
        long id = createTicket("""
                {"subject":"Valued %s","body":"b","requester_name":"R","requester_email":"%s",
                 "custom_fields":{"%s":"A-1","%s":3,"%s":"2026-03-04","%s":"pro","%s":["a","c"],"%s":true,"%s":"Long"}}
                """.formatted(unique, customer, order, count, due, plan, tags, vip, notes));

        JsonNode detail = json(get("/escalated/api/admin/tickets/" + id));
        JsonNode values = byslug(detail.get("custom_fields"));
        assertThat(values.get(order).get("value").asText()).isEqualTo("A-1");
        assertThat(values.get(count).get("value").isNumber()).isTrue();
        assertThat(values.get(count).get("value").asLong()).isEqualTo(3);
        assertThat(values.get(due).get("value").asText()).isEqualTo("2026-03-04");
        assertThat(values.get(plan).get("value").asText()).isEqualTo("pro");
        assertThat(values.get(tags).get("value").toString()).isEqualTo("[\"a\",\"c\"]");
        assertThat(values.get(vip).get("value").isBoolean()).isTrue();
        assertThat(values.get(vip).get("value").asBoolean()).isTrue();
        assertThat(values.get(notes).get("value").asText()).isEqualTo("Long");
        JsonNode definition = values.get(notes);
        assertThat(AgentInertiaPagesTest.fieldNames(definition)).contains("id", "name", "slug", "type", "options",
                "required", "placeholder", "description", "position", "value");
        assertThat(definition.get("placeholder").asText()).isEqualTo("Anything else");
        // Inactive fields and fields for users are not ticket fields.
        assertThat(values.has(retired)).isFalse();
        assertThat(values.has(userField)).isFalse();

        JsonNode agentDetail = json(get("/escalated/api/agent/tickets/" + id));
        assertThat(byslug(agentDetail.get("custom_fields")).get(plan).get("value").asText()).isEqualTo("pro");

        // The Inertia show pages carry them as a top-level prop.
        Ticket ticket = tickets.findById(id).orElseThrow();
        JsonNode agentPage = inertia(get("/escalated/agent/tickets/" + ticket.getTicketNumber()), admin.getEmail());
        assertThat(agentPage.get("component").asText()).isEqualTo("Escalated/Agent/TicketShow");
        assertThat(byslug(agentPage.get("props").get("custom_fields")).get(count).get("value").asLong()).isEqualTo(3);
        JsonNode adminPage = inertia(get("/escalated/admin/tickets/" + ticket.getTicketNumber()), admin.getEmail());
        assertThat(adminPage.get("component").asText()).isEqualTo("Escalated/Admin/Tickets/Show");
        assertThat(byslug(adminPage.get("props").get("custom_fields")).get(tags).get("value").size()).isEqualTo(2);
        JsonNode customerPage = inertia(get("/escalated/" + ticket.getTicketNumber()), customer);
        assertThat(customerPage.get("component").asText()).isEqualTo("Escalated/Customer/Show");
        assertThat(byslug(customerPage.get("props").get("custom_fields")).get(order).get("value").asText())
                .isEqualTo("A-1");

        JsonNode customerJson = json(get("/escalated/api/customer/tickets/" + id), customer);
        assertThat(byslug(customerJson.get("custom_fields")).get(due).get("value").asText()).isEqualTo("2026-03-04");
    }

    @Test
    void anInvalidValueIsAnErrorKeyedBySlugAndCreatesNoTicket() throws Exception {
        String[][] cases = {
            {"{}", order},
            {"{\"%s\":\"\"}".formatted(order), order},
            {"{\"%s\":\"A\",\"%s\":\"many\"}".formatted(order, count), count},
            {"{\"%s\":\"A\",\"%s\":\"2026-02-30\"}".formatted(order, due), due},
            {"{\"%s\":\"A\",\"%s\":\"04/03/2026\"}".formatted(order, due), due},
            {"{\"%s\":\"A\",\"%s\":\"enterprise\"}".formatted(order, plan), plan},
            {"{\"%s\":\"A\",\"%s\":[\"a\",\"z\"]}".formatted(order, tags), tags},
            {"{\"%s\":\"A\",\"%s\":\"a\"}".formatted(order, tags), tags},
            {"{\"%s\":\"A\",\"%s\":\"maybe\"}".formatted(order, vip), vip},
            {"{\"%s\":\"%s\"}".formatted(order, "x".repeat(256)), order},
            {"{\"%s\":\"A\",\"nope_%s\":\"x\"}".formatted(order, unique), "nope_" + unique},
            {"{\"%s\":\"A\",\"%s\":\"x\"}".formatted(order, retired), retired},
            {"{\"%s\":\"A\",\"%s\":\"x\"}".formatted(order, userField), userField},
        };
        for (String[] attempt : cases) {
            String subject = "Refused " + UUID.randomUUID();
            MvcResult result = mvc.perform(post("/escalated/api/admin/tickets").with(user(admin.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subject\":\"" + subject + "\",\"body\":\"b\",\"requester_name\":\"R\","
                                    + "\"requester_email\":\"" + customer + "\",\"custom_fields\":" + attempt[0] + "}"))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).as(attempt[0]).isEqualTo(422);
            JsonNode errors = JSON.readTree(result.getResponse().getContentAsString()).get("errors");
            assertThat(errors.has("custom_fields." + attempt[1])).as(attempt[0] + " -> " + errors).isTrue();
            assertThat(tickets.findAll()).as(attempt[0]).noneMatch(t -> subject.equals(t.getSubject()));
        }

        // Leaving custom_fields out altogether misses the required field too.
        mvc.perform(post("/escalated/api/admin/tickets").with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"S\",\"body\":\"b\",\"requester_name\":\"R\",\"requester_email\":\"r@x.io\"}"))
                .andExpect(status().is(422));
    }

    @Test
    void checkboxesTakeTheUsualSpellingsAndNumbersKeepTheirForm() throws Exception {
        String[][] spellings = {{"true", "true"}, {"false", "false"}, {"1", "true"}, {"0", "false"},
            {"\"1\"", "true"}, {"\"0\"", "false"}, {"\"true\"", "true"}, {"\"false\"", "false"}};
        for (String[] spelling : spellings) {
            long id = createTicket("""
                    {"subject":"Box %s","body":"b","requester_name":"R","requester_email":"%s",
                     "custom_fields":{"%s":"A","%s":%s,"%s":"2.50"}}
                    """.formatted(unique, customer, order, vip, spelling[0], count));
            JsonNode values = byslug(json(get("/escalated/api/admin/tickets/" + id)).get("custom_fields"));
            assertThat(values.get(vip).get("value").asText()).as(spelling[0]).isEqualTo(spelling[1]);
            assertThat(values.get(count).get("value").asDouble()).isEqualTo(2.5);
        }
    }

    @Test
    void anUpdateChangesOnlyTheKeysItSends() throws Exception {
        long id = createTicket("""
                {"subject":"Upd %s","body":"b","requester_name":"R","requester_email":"%s",
                 "custom_fields":{"%s":"A-1","%s":"free","%s":7}}
                """.formatted(unique, customer, order, plan, count));

        // Without custom_fields nothing changes.
        mvc.perform(put("/escalated/api/admin/tickets/" + id).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"Renamed " + unique + "\"}"))
                .andExpect(status().isOk());
        JsonNode values = byslug(json(get("/escalated/api/admin/tickets/" + id)).get("custom_fields"));
        assertThat(values.get(plan).get("value").asText()).isEqualTo("free");

        // A partial update leaves the rest; an empty optional value clears it.
        mvc.perform(put("/escalated/api/admin/tickets/" + id).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"custom_fields\":{\"%s\":\"pro\",\"%s\":null}}".formatted(plan, count)))
                .andExpect(status().isOk());
        values = byslug(json(get("/escalated/api/admin/tickets/" + id)).get("custom_fields"));
        assertThat(values.get(plan).get("value").asText()).isEqualTo("pro");
        assertThat(values.get(order).get("value").asText()).isEqualTo("A-1");
        assertThat(values.get(count).get("value").isNull()).isTrue();

        // A field may be named by its id too.
        String countId = String.valueOf(customFields.findByFieldKey(count).orElseThrow().getId());
        mvc.perform(put("/escalated/api/admin/tickets/" + id).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"custom_fields\":{\"%s\":11}}".formatted(countId)))
                .andExpect(status().isOk());
        values = byslug(json(get("/escalated/api/admin/tickets/" + id)).get("custom_fields"));
        assertThat(values.get(count).get("value").asLong()).isEqualTo(11);

        // A sent required field cannot be blank, and a refused update changes nothing.
        MvcResult refused = mvc.perform(put("/escalated/api/admin/tickets/" + id).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Never " + unique + "\",\"custom_fields\":{\"%s\":\"\",\"%s\":\"free\"}}"
                                .formatted(order, plan)))
                .andExpect(status().is(422))
                .andReturn();
        assertThat(refused.getResponse().getContentAsString()).contains("custom_fields." + order);
        assertThat(tickets.findById(id).orElseThrow().getSubject()).isEqualTo("Renamed " + unique);
        values = byslug(json(get("/escalated/api/admin/tickets/" + id)).get("custom_fields"));
        assertThat(values.get(plan).get("value").asText()).isEqualTo("pro");
    }

    @Test
    void theCustomerPagesOfferTheFieldsAndValidateThem() throws Exception {
        JsonNode create = inertia(get("/escalated/create"), customer);
        assertThat(create.get("component").asText()).isEqualTo("Escalated/Customer/Create");
        JsonNode definitions = byslug(create.get("props").get("custom_fields"));
        assertThat(definitions.has(order)).isTrue();
        assertThat(definitions.has(retired)).isFalse();
        assertThat(definitions.has(userField)).isFalse();
        assertThat(AgentInertiaPagesTest.fieldNames(definitions.get(plan))).containsExactly("id", "name", "slug",
                "type", "options", "required", "placeholder", "description", "position");
        assertThat(definitions.get(plan).get("options").toString()).isEqualTo("[\"free\",\"pro\"]");
        assertThat(definitions.get(order).get("required").asBoolean()).isTrue();
        List<Integer> positions = new ArrayList<>();
        create.get("props").get("custom_fields").forEach(field -> positions.add(field.get("position").asInt()));
        assertThat(positions).isSorted();

        // Missing the required field: back with errors, and no ticket.
        String subject = "Portal " + unique;
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated").session(session).with(user(customer)).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost/escalated/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"" + subject + "\",\"description\":\"Help\","
                                + "\"custom_fields\":{\"%s\":\"pro\"}}".formatted(plan)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/create"));
        JsonNode errors = inertia(get("/escalated/create").session(session), customer).get("props").get("errors");
        assertThat(errors.get("custom_fields." + order).asText()).isEqualTo("The Order " + unique + " field is required.");
        assertThat(tickets.findAll()).noneMatch(t -> subject.equals(t.getSubject()));

        MvcResult stored = mvc.perform(post("/escalated").with(user(customer)).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"" + subject + "\",\"description\":\"Help\","
                                + "\"custom_fields\":{\"%s\":\"PO-9\",\"%s\":[\"b\"]}}".formatted(order, tags)))
                .andExpect(status().isSeeOther())
                .andReturn();
        String location = stored.getResponse().getHeader("Location");
        JsonNode show = inertia(get(location), customer);
        JsonNode values = byslug(show.get("props").get("custom_fields"));
        assertThat(values.get(order).get("value").asText()).isEqualTo("PO-9");
        assertThat(values.get(tags).get("value").toString()).isEqualTo("[\"b\"]");
        assertThat(values.get(plan).get("value").isNull()).isTrue();
    }

    @Test
    void theCustomerApiTakesAndShowsValues() throws Exception {
        MvcResult refused = mvc.perform(post("/escalated/api/customer/tickets").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Api " + unique + "\",\"body\":\"b\"}"))
                .andExpect(status().is(422))
                .andReturn();
        assertThat(refused.getResponse().getContentAsString()).contains("custom_fields." + order);

        MvcResult result = mvc.perform(post("/escalated/api/customer/tickets").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Api " + unique + "\",\"body\":\"b\","
                                + "\"custom_fields\":{\"%s\":\"C-3\",\"%s\":\"0\"}}".formatted(order, vip)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        JsonNode values = byslug(body.get("custom_fields"));
        assertThat(values.get(order).get("value").asText()).isEqualTo("C-3");
        assertThat(values.get(vip).get("value").asBoolean()).isFalse();
        assertThat(values.get(vip).get("value").isBoolean()).isTrue();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Creates a field over the JSON admin API, its name and slug suffixed; answers its slug. */
    private String field(String template) throws Exception {
        String body = template.replace("%s", unique);
        MvcResult result = mvc.perform(post("/escalated/api/admin/custom-fields").with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode field = JSON.readTree(result.getResponse().getContentAsString());
        created.add(field.get("id").asLong());
        return JSON.readTree(body).get("slug").asText();
    }

    private long createTicket(String json) throws Exception {
        MvcResult result = mvc.perform(post("/escalated/api/admin/tickets").with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        return json(request, admin.getEmail());
    }

    private JsonNode json(MockHttpServletRequestBuilder request, String who) throws Exception {
        MvcResult result = mvc.perform(request.with(user(who))).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode inertia(MockHttpServletRequestBuilder request, String who) throws Exception {
        MvcResult result = mvc.perform(request.with(user(who))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    /** The {@code custom_fields} list as an object keyed by slug. */
    private static JsonNode byslug(JsonNode list) {
        assertThat(list).as("custom_fields").isNotNull();
        com.fasterxml.jackson.databind.node.ObjectNode keyed = JSON.createObjectNode();
        list.forEach(field -> keyed.set(field.get("slug").asText(), field));
        return keyed;
    }
}
