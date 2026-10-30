package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Permission;
import dev.escalated.models.Role;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.PermissionRepository;
import dev.escalated.repositories.RoleRepository;
import dev.escalated.services.PermissionSeeder;
import hostapp.HostApplication;
import java.util.ArrayList;
import java.util.HashSet;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * The users screen's roles picker ({@code PUT escalated.admin.users.roles} with
 * {@code {role_ids: [...]}}), against escalated-laravel's {@code UserController::updateRoles}: only
 * while roles and permissions is on, needs {@code role.manage}, validates the ids, refuses removing
 * your own role management and goes back with "User updated.". An agent profile holds one role
 * here, so the set maps to one role.
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
class AdminUserRolesInertiaTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private PermissionSeeder seeder;
    @Autowired private EscalatedProperties properties;

    private String unique;
    private AgentProfile admin;
    private AgentProfile agent;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        properties.getAccess().setRolesPermissions(true);
        seeder.seed();
        admin = profile("admin", true, role("admin"));
        agent = profile("agent", false, role("agent"));
    }

    @AfterEach
    void switchRolesOff() {
        properties.getAccess().setRolesPermissions(null);
    }

    @Test
    void offTheRouteIsNotThereAndTheScreenSendsNoRoles() throws Exception {
        properties.getAccess().setRolesPermissions(false);
        send(admin, agent.getId(), "{\"role_ids\":[" + role("light_agent").getId() + "]}", new MockHttpSession())
                .andExpect(status().isNotFound());
        assertThat(roleOf(agent)).isEqualTo("agent");

        JsonNode props = page(admin, new MockHttpSession()).get("props");
        assertThat(props.has("roles")).isFalse();
        assertThat(props.get("users").get("data").get(0).has("roles")).isFalse();
    }

    @Test
    void onTheScreenSendsTheRolesAndEachUsersRoleSlugs() throws Exception {
        JsonNode page = JSON.readTree(mvc.perform(get("/escalated/admin/users").param("search", unique)
                        .with(user(admin.getEmail())).header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode props = page.get("props");

        List<String> names = new ArrayList<>();
        List<String> slugs = new ArrayList<>();
        for (JsonNode role : props.get("roles")) {
            names.add(role.get("name").asText());
            slugs.add(role.get("slug").asText());
            assertThat(AgentInertiaPagesTest.fieldNames(role)).containsExactly("id", "name", "slug");
        }
        assertThat(names).isSorted();
        assertThat(slugs).contains("admin", "agent", "light_agent");

        for (JsonNode row : props.get("users").get("data")) {
            String expected = row.get("id").asLong() == admin.getId() ? "admin" : "agent";
            assertThat(row.get("roles").get(0).asText()).isEqualTo(expected);
            assertThat(row.get("roles").size()).isEqualTo(1);
        }
    }

    @Test
    void onARoleIsReplacedAndTheScreenGoesBackWithTheFlash() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Role light = role("light_agent");
        send(admin, agent.getId(), "{\"role_ids\":[" + light.getId() + "]}", session).andExpect(status().isSeeOther());
        assertThat(page(admin, session).get("props").get("flash").get("success").asText()).isEqualTo("User updated.");
        assertThat(roleOf(agent)).isEqualTo("light_agent");
        assertThat(agents.findPermissionNamesByEmail(agent.getEmail()))
                .contains("reply.create_internal").doesNotContain("ticket.assign");

        // The picker sends the whole set: the current role plus the newly ticked one.
        send(admin, agent.getId(), "{\"role_ids\":[" + light.getId() + "," + role("agent").getId() + "]}", session)
                .andExpect(status().isSeeOther());
        assertThat(roleOf(agent)).isEqualTo("agent");

        send(admin, agent.getId(), "{\"role_ids\":[]}", session).andExpect(status().isSeeOther());
        assertThat(roleOf(agent)).isNull();
    }

    @Test
    void onTwoNewRolesAtOnceAreRefused() throws Exception {
        MockHttpSession session = new MockHttpSession();
        send(admin, agent.getId(), "{\"role_ids\":[" + role("light_agent").getId() + "," + role("admin").getId() + "]}",
                session).andExpect(status().isSeeOther());
        assertThat(errorKeys(session)).containsExactly("role_ids");
        assertThat(roleOf(agent)).isEqualTo("agent");
    }

    @Test
    void onRoleIdsAreValidatedAsLaravelDoes() throws Exception {
        String[][] cases = {
            {"{}", "role_ids"},
            {"{\"role_ids\":\"admin\"}", "role_ids"},
            {"{\"role_ids\":[\"x\"]}", "role_ids.0"},
            {"{\"role_ids\":[987654]}", "role_ids.0"},
        };
        for (String[] each : cases) {
            MockHttpSession session = new MockHttpSession();
            send(admin, agent.getId(), each[0], session).andExpect(status().isSeeOther());
            assertThat(errorKeys(session)).as(each[0]).containsExactly(each[1]);
        }
        assertThat(roleOf(agent)).isEqualTo("agent");
    }

    @Test
    void onRoleChangesNeedRoleManage() throws Exception {
        Role userAdmins = new Role();
        userAdmins.setName("user-admins-" + unique);
        userAdmins.setPermissions(new HashSet<>(List.of(
                permissions.findByName("user.view").orElseThrow(), permissions.findByName("user.manage").orElseThrow())));
        userAdmins = roles.save(userAdmins);
        AgentProfile manager = profile("manager", true, userAdmins);

        send(manager, agent.getId(), "{\"role_ids\":[" + role("light_agent").getId() + "]}", new MockHttpSession())
                .andExpect(status().isForbidden());
        assertThat(roleOf(agent)).isEqualTo("agent");
    }

    @Test
    void onARoleManagerCannotRemoveTheirOwnRoleManagement() throws Exception {
        MockHttpSession session = new MockHttpSession();
        send(admin, admin.getId(), "{\"role_ids\":[" + role("light_agent").getId() + "]}", session)
                .andExpect(status().isSeeOther());
        assertThat(page(admin, session).get("props").get("flash").get("error").asText())
                .isEqualTo("You cannot remove your own role management.");
        assertThat(roleOf(admin)).isEqualTo("admin");
    }

    @Test
    void onAnUnknownUserIs404() throws Exception {
        send(admin, 987654321L, "{\"role_ids\":[]}", new MockHttpSession()).andExpect(status().isNotFound());
    }

    private Role role(String name) {
        return roles.findByName(name).orElseThrow();
    }

    private AgentProfile profile(String kind, boolean isAdmin, Role role) {
        AgentProfile profile = new AgentProfile();
        profile.setName(kind + " " + unique);
        profile.setEmail(kind + "-" + unique + "@example.com");
        profile.setAgent(true);
        profile.setAdmin(isAdmin);
        profile.setRole(role);
        return agents.save(profile);
    }

    private String roleOf(AgentProfile profile) {
        Role role = agents.findById(profile.getId()).orElseThrow().getRole();
        // The proxy's id is readable outside a session; its name is not.
        return role == null ? null : roles.findById(role.getId()).orElseThrow().getName();
    }

    private ResultActions send(AgentProfile actor, Long target, String json, MockHttpSession session) throws Exception {
        return mvc.perform(put("/escalated/admin/users/" + target + "/roles").session(session)
                .with(user(actor.getEmail())).with(UiTestSupport.xsrf()).header("X-Inertia", "true")
                .header("Referer", "http://localhost/escalated/admin/users")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode page(AgentProfile actor, MockHttpSession session) throws Exception {
        return JSON.readTree(mvc.perform(get("/escalated/admin/users").session(session).with(user(actor.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private List<String> errorKeys(MockHttpSession session) throws Exception {
        return AgentInertiaPagesTest.fieldNames(page(admin, session).get("props").get("errors"));
    }
}
