package dev.escalated.ui;

import static dev.escalated.ui.AgentInertiaPagesTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.CustomField;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.CustomFieldRepository;
import hostapp.HostApplication;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The custom field and custom object admin pages over the Inertia protocol, and their writes. */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class CustomFieldsInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private CustomFieldRepository customFields;

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

    @AfterEach
    void forgetFields() {
        customFields.findAll().stream()
                .filter(field -> field.getName().contains(unique))
                .forEach(customFields::delete);
    }

    @Test
    void anAgentWhoIsNotAnAdminIsRefused() throws Exception {
        for (String path : new String[] {"/escalated/admin/custom-fields", "/escalated/admin/custom-fields/create",
                "/escalated/admin/custom-objects", "/escalated/admin/custom-objects/create"}) {
            mvc.perform(get(path).with(user(agent.getEmail()))).andExpect(status().isForbidden());
        }
    }

    @Test
    void customFieldsAreListedCreatedEditedReorderedAndDeleted() throws Exception {
        JsonNode create = inertia(get("/escalated/admin/custom-fields/create"));
        assertThat(create.get("component").asText()).isEqualTo("Escalated/Admin/CustomFields/Form");
        assertThat(create.get("props").get("contexts").toString()).isEqualTo("[\"ticket\",\"user\",\"organization\"]");
        assertThat(create.get("props").has("field")).isFalse();

        // As the shared form posts it: no slug, which is made from the name.
        write(post("/escalated/admin/custom-fields"), """
                {"name":"Order Number %s","type":"select","context":"ticket","options":["A","B"],
                 "required":true,"placeholder":"Pick one","description":"Which order","position":7,"active":true}
                """.formatted(unique))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/custom-fields"));
        CustomField created = customFields.findByFieldKey("order_number_" + unique).orElseThrow();

        // A second field with the same name gets the next free slug.
        write(post("/escalated/admin/custom-fields"), """
                {"name":"Order Number %s","type":"text","context":"ticket","options":[],"required":false,
                 "placeholder":"","description":"","position":8,"active":true}
                """.formatted(unique))
                .andExpect(status().isSeeOther());
        assertThat(customFields.findByFieldKey("order_number_" + unique + "_2")).isPresent();

        JsonNode index = inertia(get("/escalated/admin/custom-fields"));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/CustomFields/Index");
        JsonNode row = find(index.get("props").get("fields"), created.getId());
        assertThat(fieldNames(row)).contains("id", "name", "slug", "type", "context", "options", "required",
                "placeholder", "description", "validation_rules", "conditions", "position", "active",
                "created_at", "updated_at");
        assertThat(row.get("slug").asText()).isEqualTo("order_number_" + unique);
        assertThat(row.get("type").asText()).isEqualTo("select");
        assertThat(row.get("context").asText()).isEqualTo("ticket");
        assertThat(row.get("options").toString()).isEqualTo("[\"A\",\"B\"]");
        assertThat(row.get("required").asBoolean()).isTrue();
        assertThat(row.get("position").asInt()).isEqualTo(7);
        assertThat(row.get("active").asBoolean()).isTrue();

        JsonNode edit = inertia(get("/escalated/admin/custom-fields/" + created.getId() + "/edit"));
        assertThat(edit.get("component").asText()).isEqualTo("Escalated/Admin/CustomFields/Form");
        assertThat(edit.get("props").get("field").get("id").asLong()).isEqualTo(created.getId());
        assertThat(edit.get("props").get("field").get("placeholder").asText()).isEqualTo("Pick one");
        assertThat(edit.get("props").get("contexts").size()).isEqualTo(3);

        write(put("/escalated/admin/custom-fields/" + created.getId()), """
                {"name":"Order %s","type":"multi_select","context":"user","options":["A","B","C"],
                 "required":false,"placeholder":"","description":"","position":2,"active":false}
                """.formatted(unique))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/custom-fields"));
        CustomField updated = customFields.findById(created.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Order " + unique);
        assertThat(updated.getFieldKey()).isEqualTo("order_number_" + unique);
        assertThat(updated.getFieldType()).isEqualTo("multi_select");
        assertThat(updated.isActive()).isFalse();
        write(patch("/escalated/admin/custom-fields/" + created.getId()), """
                {"name":"Order %s","type":"text","context":"ticket","active":true}
                """.formatted(unique))
                .andExpect(status().isSeeOther());
        assertThat(customFields.findById(created.getId()).orElseThrow().getFieldType()).isEqualTo("text");

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/custom-fields/reorder").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/custom-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positions\":[{\"id\":" + created.getId() + ",\"position\":42}]}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/custom-fields"));
        assertThat(customFields.findById(created.getId()).orElseThrow().getSortOrder()).isEqualTo(42);
        JsonNode flashed = inertia(get("/escalated/admin/custom-fields").session(session));
        assertThat(flashed.get("props").get("flash").get("success").asText()).isEqualTo("Fields reordered.");

        session = new MockHttpSession();
        mvc.perform(delete("/escalated/admin/custom-fields/" + created.getId()).session(session)
                        .with(user(admin.getEmail())).with(UiTestSupport.xsrf()).header("X-Inertia", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/custom-fields"));
        assertThat(customFields.findById(created.getId())).isEmpty();
        flashed = inertia(get("/escalated/admin/custom-fields").session(session));
        assertThat(flashed.get("props").get("flash").get("success").asText()).isEqualTo("Custom field deleted.");
    }

    @Test
    void aSelectFieldWithoutOptionsOrABadTypeGoesBackWithErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/custom-fields").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/custom-fields/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pick " + unique + "\",\"type\":\"select\",\"context\":\"ticket\","
                                + "\"options\":[]}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/custom-fields/create"));
        JsonNode errors = inertia(get("/escalated/admin/custom-fields/create").session(session))
                .get("props").get("errors");
        assertThat(errors.has("options")).isTrue();

        session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/custom-fields").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"type\":\"colour\",\"context\":\"planet\"}"))
                .andExpect(status().isSeeOther());
        errors = inertia(get("/escalated/admin/custom-fields/create").session(session)).get("props").get("errors");
        assertThat(errors.get("name").asText()).isEqualTo("The name field is required.");
        assertThat(errors.has("type")).isTrue();
        assertThat(errors.has("context")).isTrue();
        assertThat(customFields.findAll()).noneMatch(field -> field.getName().contains("Pick " + unique));
    }

    @Test
    void customObjectsAndTheirRecordsAreManaged() throws Exception {
        JsonNode create = inertia(get("/escalated/admin/custom-objects/create"));
        assertThat(create.get("component").asText()).isEqualTo("Escalated/Admin/CustomObjects/Form");

        String slug = "assets-" + unique;
        write(post("/escalated/admin/custom-objects"), """
                {"name":"Assets %s","slug":"%s","fields_schema":[
                  {"name":"serial","type":"text","required":true,"options":[]},
                  {"name":"cost","type":"number","required":false,"options":[]},
                  {"name":"bought","type":"date","required":false,"options":[]},
                  {"name":"tier","type":"select","required":false,"options":["gold","silver"]}]}
                """.formatted(unique, slug))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/custom-objects"));

        JsonNode index = inertia(get("/escalated/admin/custom-objects"));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/CustomObjects/Index");
        JsonNode object = null;
        for (JsonNode candidate : index.get("props").get("objects")) {
            if (candidate.get("slug").asText().equals(slug)) {
                object = candidate;
            }
        }
        assertThat(object).isNotNull();
        assertThat(fieldNames(object)).contains("id", "name", "slug", "fields_schema", "records_count",
                "created_at", "updated_at");
        assertThat(object.get("fields_schema").size()).isEqualTo(4);
        assertThat(object.get("records_count").asLong()).isZero();
        long id = object.get("id").asLong();

        // The same slug again is refused.
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/custom-objects").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Again\",\"slug\":\"" + slug + "\","
                                + "\"fields_schema\":[{\"name\":\"x\",\"type\":\"text\"}]}"))
                .andExpect(status().isSeeOther());
        JsonNode errors = inertia(get("/escalated/admin/custom-objects/create").session(session))
                .get("props").get("errors");
        assertThat(errors.get("slug").asText()).isEqualTo("The slug has already been taken.");

        // An empty schema and a bad field type are refused.
        session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/custom-objects").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"slug\":\"bad slug!\",\"fields_schema\":[]}"))
                .andExpect(status().isSeeOther());
        errors = inertia(get("/escalated/admin/custom-objects/create").session(session)).get("props").get("errors");
        assertThat(errors.has("slug")).isTrue();
        assertThat(errors.has("fields_schema")).isTrue();

        JsonNode edit = inertia(get("/escalated/admin/custom-objects/" + id + "/edit"));
        assertThat(edit.get("component").asText()).isEqualTo("Escalated/Admin/CustomObjects/Form");
        assertThat(edit.get("props").get("object").get("slug").asText()).isEqualTo(slug);

        // Records: as the shared page posts them, every schema field present, blanks included.
        String recordsPath = "/escalated/admin/custom-objects/" + id + "/records";
        mvc.perform(post(recordsPath).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").header("Referer", "http://localhost" + recordsPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"serial\":\"SN-1\",\"cost\":\"12.5\",\"bought\":\"2026-01-31\","
                                + "\"tier\":\"gold\"}}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", recordsPath));
        write(post(recordsPath), "{\"data\":{\"serial\":\"SN-2\",\"cost\":\"\",\"bought\":\"\",\"tier\":\"\"}}")
                .andExpect(status().isSeeOther());

        JsonNode records = inertia(get(recordsPath));
        assertThat(records.get("component").asText()).isEqualTo("Escalated/Admin/CustomObjects/Records");
        assertThat(fieldNames(records.get("props").get("object"))).contains("id", "name", "fields_schema");
        JsonNode list = records.get("props").get("records");
        assertThat(list.size()).isEqualTo(2);
        assertThat(fieldNames(list.get(0))).contains("id", "object_id", "data", "created_at", "updated_at");
        assertThat(list.get(0).get("data").get("serial").asText()).isEqualTo("SN-2");
        assertThat(list.get(1).get("data").get("tier").asText()).isEqualTo("gold");
        long first = list.get(1).get("id").asLong();

        // Record data is checked against the schema.
        for (String bad : new String[] {
            "{\"data\":{\"serial\":\"\"}}",
            "{\"data\":{\"serial\":\"S\",\"cost\":\"lots\"}}",
            "{\"data\":{\"serial\":\"S\",\"bought\":\"2026-02-30\"}}",
            "{\"data\":{\"serial\":\"S\",\"tier\":\"bronze\"}}",
            "{\"data\":{\"serial\":\"S\",\"colour\":\"red\"}}",
            "{\"data\":\"nope\"}",
        }) {
            session = new MockHttpSession();
            mvc.perform(post(recordsPath).session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                            .header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isSeeOther());
            JsonNode shown = inertia(get(recordsPath).session(session)).get("props");
            assertThat(shown.get("errors").size()).as(bad).isGreaterThan(0);
            assertThat(shown.get("records").size()).as(bad).isEqualTo(2);
        }
        session = new MockHttpSession();
        mvc.perform(post(recordsPath).session(session).with(user(admin.getEmail())).with(UiTestSupport.xsrf())
                        .header("X-Inertia", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"serial\":\"S\",\"cost\":\"lots\",\"colour\":\"red\"}}"))
                .andExpect(status().isSeeOther());
        errors = inertia(get(recordsPath).session(session)).get("props").get("errors");
        assertThat(errors.has("data.cost")).isTrue();
        assertThat(errors.has("data.colour")).isTrue();

        write(put(recordsPath + "/" + first), "{\"data\":{\"serial\":\"SN-1b\",\"tier\":\"silver\"}}")
                .andExpect(status().isSeeOther());
        JsonNode afterUpdate = inertia(get(recordsPath)).get("props").get("records");
        assertThat(afterUpdate.get(1).get("data").get("serial").asText()).isEqualTo("SN-1b");

        // A record id that belongs to another object is a 404.
        write(post("/escalated/admin/custom-objects"), """
                {"name":"Other %s","slug":"other-%s","fields_schema":[{"name":"serial","type":"text"}]}
                """.formatted(unique, unique)).andExpect(status().isSeeOther());
        long other = 0;
        for (JsonNode candidate : inertia(get("/escalated/admin/custom-objects")).get("props").get("objects")) {
            if (candidate.get("slug").asText().equals("other-" + unique)) {
                other = candidate.get("id").asLong();
            }
        }
        write(put("/escalated/admin/custom-objects/" + other + "/records/" + first), "{\"data\":{\"serial\":\"X\"}}")
                .andExpect(status().isNotFound());
        write(delete("/escalated/admin/custom-objects/" + other + "/records/" + first), "{}")
                .andExpect(status().isNotFound());

        write(delete(recordsPath + "/" + first), "{}").andExpect(status().isSeeOther());
        assertThat(inertia(get(recordsPath)).get("props").get("records").size()).isEqualTo(1);

        write(put("/escalated/admin/custom-objects/" + id), """
                {"name":"Kit %s","slug":"%s","fields_schema":[{"name":"serial","type":"text","required":true}]}
                """.formatted(unique, slug))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/custom-objects"));
        JsonNode renamed = inertia(get("/escalated/admin/custom-objects/" + id + "/edit")).get("props").get("object");
        assertThat(renamed.get("name").asText()).isEqualTo("Kit " + unique);
        JsonNode counted = null;
        for (JsonNode candidate : inertia(get("/escalated/admin/custom-objects")).get("props").get("objects")) {
            if (candidate.get("id").asLong() == id) {
                counted = candidate;
            }
        }
        assertThat(counted.get("records_count").asLong()).isEqualTo(1);

        // Deleting the object deletes its records.
        write(delete("/escalated/admin/custom-objects/" + id), "{}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/custom-objects"));
        mvc.perform(get(recordsPath).with(user(admin.getEmail())).header("X-Inertia", "true")
                        .header("X-Inertia-Version", "build-1"))
                .andExpect(status().isNotFound());
        write(delete("/escalated/admin/custom-objects/" + other), "{}").andExpect(status().isSeeOther());
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
