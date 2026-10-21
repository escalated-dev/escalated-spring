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
import dev.escalated.models.ApiToken;
import dev.escalated.models.Permission;
import dev.escalated.models.Role;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ApiTokenRepository;
import dev.escalated.repositories.PermissionRepository;
import dev.escalated.repositories.RoleRepository;
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

/** The people pages over the Inertia protocol: users, roles and API tokens. */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class AdminPeopleInertiaPagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private ApiTokenRepository apiTokens;

    private String unique;
    private String totpSeed;
    private AgentProfile admin;
    private AgentProfile agent;
    private Permission viewTickets;
    private Permission manageRoles;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        totpSeed = "TOTP" + unique.toUpperCase();
        admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin.setTwoFactorSecret(totpSeed);
        admin = agents.save(admin);

        agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent = agents.save(agent);

        viewTickets = permission("tickets.view-" + unique, "tickets");
        manageRoles = permission("roles.manage-" + unique, "roles");
    }

    @Test
    void anAgentWhoIsNotAnAdminIsRefused() throws Exception {
        for (String path : new String[] {"/escalated/admin/users", "/escalated/admin/roles",
                "/escalated/admin/roles/create", "/escalated/admin/api-tokens"}) {
            mvc.perform(get(path).with(user(agent.getEmail()))).andExpect(status().isForbidden());
        }
    }

    @Test
    void usersAreListedSearchedAndTheirRolesChanged() throws Exception {
        JsonNode page = inertia(get("/escalated/admin/users").param("search", "agent-" + unique));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Users/Index");
        JsonNode props = page.get("props");
        assertThat(fieldNames(props)).contains("users", "filters", "currentUserId");
        assertThat(props.get("currentUserId").asLong()).isEqualTo(admin.getId());
        assertThat(props.get("filters").get("search").asText()).isEqualTo("agent-" + unique);
        JsonNode users = props.get("users");
        assertThat(fieldNames(users)).contains("data", "current_page", "last_page", "links", "total");
        assertThat(users.get("total").asLong()).isEqualTo(1);
        JsonNode row = users.get("data").get(0);
        assertThat(fieldNames(row)).containsExactly("id", "name", "email", "is_admin", "is_agent");
        assertThat(row.get("is_agent").asBoolean()).isTrue();
        assertThat(row.get("is_admin").asBoolean()).isFalse();

        write(patch("/escalated/admin/users/" + agent.getId() + "/role"), "{\"role\":\"admin\",\"value\":true}")
                .andExpect(status().isSeeOther());
        assertThat(agents.findById(agent.getId()).orElseThrow().isAdmin()).isTrue();

        write(patch("/escalated/admin/users/" + agent.getId() + "/role"), "{\"role\":\"agent\",\"value\":false}")
                .andExpect(status().isSeeOther());
        AgentProfile demoted = agents.findById(agent.getId()).orElseThrow();
        assertThat(demoted.isAgent()).isFalse();
        assertThat(demoted.isAdmin()).isFalse();
        assertThat(inertia(get("/escalated/admin/users")).toString()).doesNotContain(totpSeed);
    }

    @Test
    void anAdminCannotRemoveTheirOwnAdminRole() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(patch("/escalated/admin/users/" + admin.getId() + "/role").session(session)
                        .with(user(admin.getEmail())).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/users")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"admin\",\"value\":false}"))
                .andExpect(status().isSeeOther());
        assertThat(inertia(get("/escalated/admin/users").session(session)).get("props").get("flash").get("error")
                .asText()).isEqualTo("You cannot remove your own admin role.");
        assertThat(agents.findById(admin.getId()).orElseThrow().isAdmin()).isTrue();
    }

    @Test
    void anUnknownRoleIsAnError() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(patch("/escalated/admin/users/" + agent.getId() + "/role").session(session)
                        .with(user(admin.getEmail())).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/users")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"owner\",\"value\":true}"))
                .andExpect(status().isSeeOther());
        assertThat(inertia(get("/escalated/admin/users").session(session)).get("props").get("errors").get("role")
                .asText()).isEqualTo("The selected role is invalid.");
    }

    @Test
    void rolesAreListedCreatedEditedAndDeleted() throws Exception {
        JsonNode create = inertia(get("/escalated/admin/roles/create"));
        assertThat(create.get("component").asText()).isEqualTo("Escalated/Admin/Roles/Form");
        JsonNode groups = create.get("props").get("permissions");
        assertThat(groups.has("tickets")).isTrue();
        JsonNode listed = find(groups.get("tickets"), viewTickets.getId());
        assertThat(fieldNames(listed)).contains("id", "name", "slug", "group", "description");
        assertThat(listed.get("slug").asText()).isEqualTo("tickets.view-" + unique);

        write(post("/escalated/admin/roles"), "{\"name\":\"Leads " + unique + "\",\"description\":\"Team leads\","
                + "\"permissions\":[" + viewTickets.getId() + "]}")
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/roles"));
        Role created = roles.findAll().stream().filter(r -> r.getName().equals("Leads " + unique)).findFirst().orElseThrow();
        agent.setRole(created);
        agents.save(agent);

        JsonNode index = inertia(get("/escalated/admin/roles"));
        assertThat(index.get("component").asText()).isEqualTo("Escalated/Admin/Roles/Index");
        JsonNode row = find(index.get("props").get("roles"), created.getId());
        assertThat(fieldNames(row)).contains("id", "name", "description", "is_system", "users_count");
        assertThat(row.get("users_count").asInt()).isEqualTo(1);

        JsonNode edit = inertia(get("/escalated/admin/roles/" + created.getId() + "/edit"));
        JsonNode role = edit.get("props").get("role");
        assertThat(fieldNames(role)).contains("id", "name", "description", "permissions");
        assertThat(role.get("permissions").get(0).get("id").asLong()).isEqualTo(viewTickets.getId());

        write(put("/escalated/admin/roles/" + created.getId()), "{\"name\":\"Leads " + unique + "\","
                + "\"permissions\":[" + manageRoles.getId() + "]}")
                .andExpect(status().isSeeOther());
        JsonNode updated = inertia(get("/escalated/admin/roles/" + created.getId() + "/edit")).get("props").get("role");
        assertThat(updated.get("permissions").size()).isEqualTo(1);
        assertThat(updated.get("permissions").get(0).get("id").asLong()).isEqualTo(manageRoles.getId());

        write(delete("/escalated/admin/roles/" + created.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(roles.existsById(created.getId())).isFalse();
        assertThat(agents.findById(agent.getId()).orElseThrow().getRole()).isNull();
    }

    @Test
    void aSystemRoleIsNeitherChangedNorDeleted() throws Exception {
        Role system = new Role();
        system.setName("System " + unique);
        system.setSystem(true);
        system = roles.save(system);
        MockHttpSession session = new MockHttpSession();

        mvc.perform(delete("/escalated/admin/roles/" + system.getId()).session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/roles"))
                .andExpect(status().isSeeOther());
        assertThat(inertia(get("/escalated/admin/roles").session(session)).get("props").get("flash").get("error")
                .asText()).isEqualTo("System roles cannot be deleted.");
        assertThat(roles.existsById(system.getId())).isTrue();

        write(put("/escalated/admin/roles/" + system.getId()), "{\"name\":\"Renamed " + unique + "\"}")
                .andExpect(status().isSeeOther());
        assertThat(roles.findById(system.getId()).orElseThrow().getName()).isEqualTo("System " + unique);
    }

    @Test
    void anUnknownPermissionIsAnError() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/roles").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/roles/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad " + unique + "\",\"permissions\":[999999999]}"))
                .andExpect(status().isSeeOther());
        assertThat(inertia(get("/escalated/admin/roles/create").session(session)).get("props").get("errors")
                .has("permissions")).isTrue();
        assertThat(roles.findAll().stream().noneMatch(r -> r.getName().equals("Bad " + unique))).isTrue();
    }

    @Test
    void apiTokensAreIssuedOnceListedRenamedAndRevoked() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/api-tokens").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/api-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CI " + unique + "\",\"user_id\":" + agent.getId()
                                + ",\"abilities\":[\"agent\"],\"expires_in_days\":30}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/escalated/admin/api-tokens"));

        JsonNode page = inertia(get("/escalated/admin/api-tokens").session(session));
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/ApiTokens/Index");
        JsonNode props = page.get("props");
        String plainText = props.get("flash").get("plain_text_token").asText();
        assertThat(plainText).isNotBlank();
        assertThat(props.get("flash").get("success").asText()).isEqualTo("API token created.");
        assertThat(fieldNames(props)).contains("tokens", "users", "api_enabled");
        assertThat(props.get("api_enabled").asBoolean()).isTrue();
        assertThat(fieldNames(find(props.get("users"), agent.getId()))).containsExactly("id", "name", "email");

        ApiToken issued = apiTokens.findAll().stream().filter(t -> t.getName().equals("CI " + unique))
                .findFirst().orElseThrow();
        JsonNode row = find(props.get("tokens"), issued.getId());
        assertThat(fieldNames(row)).contains("id", "name", "user_name", "user_email", "abilities", "last_used_at",
                "expires_at", "is_expired", "created_at");
        assertThat(row.get("user_name").asText()).isEqualTo(agent.getName());
        assertThat(row.get("abilities").get(0).asText()).isEqualTo("agent");
        assertThat(row.get("expires_at").isTextual()).isTrue();
        assertThat(row.get("is_expired").asBoolean()).isFalse();

        // Shown once: the next page has no plain text, and no page carries the hash.
        JsonNode again = inertia(get("/escalated/admin/api-tokens").session(session));
        assertThat(again.get("props").get("flash").has("plain_text_token")).isFalse();
        assertThat(again.toString()).doesNotContain(plainText).doesNotContain(issued.getTokenHash())
                .doesNotContain(totpSeed);

        write(put("/escalated/admin/api-tokens/" + issued.getId()), "{\"name\":\"Deploy " + unique + "\"}")
                .andExpect(status().isSeeOther());
        assertThat(apiTokens.findById(issued.getId()).orElseThrow().getName()).isEqualTo("Deploy " + unique);

        write(delete("/escalated/admin/api-tokens/" + issued.getId()), "{}").andExpect(status().isSeeOther());
        assertThat(apiTokens.existsById(issued.getId())).isFalse();
    }

    @Test
    void aTokenWithoutANameOrUserGoesBackWithErrors() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/escalated/admin/api-tokens").session(session).with(user(admin.getEmail()))
                        .with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                        .header("Referer", "http://localhost/escalated/admin/api-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"user_id\":\"\",\"abilities\":[\"agent\"],\"expires_in_days\":null}"))
                .andExpect(status().isSeeOther());
        JsonNode errors = inertia(get("/escalated/admin/api-tokens").session(session)).get("props").get("errors");
        assertThat(errors.has("name")).isTrue();
        assertThat(errors.has("agent_id")).isTrue();
    }

    @Test
    void theRouteTableNamesWhatThePagesCall() throws Exception {
        String html = mvc.perform(get("/escalated/admin/users").with(user(admin.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("\"escalated.admin.users.role\":\"/escalated/admin/users/{user}/role\"",
                "\"escalated.admin.roles.edit\":\"/escalated/admin/roles/{role}/edit\"",
                "\"escalated.admin.roles.destroy\":\"/escalated/admin/roles/{role}\"");
    }

    private Permission permission(String name, String category) {
        Permission permission = new Permission();
        permission.setName(name);
        permission.setCategory(category);
        permission.setDescription("Seeded " + name);
        return permissions.save(permission);
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
