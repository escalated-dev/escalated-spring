package dev.escalated.security;

import static dev.escalated.security.AccessTestSupport.api;
import static dev.escalated.security.AccessTestSupport.page;
import static dev.escalated.security.AccessTestSupport.shared;
import static dev.escalated.security.AccessTestSupport.staff;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.SettingsService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Roles and permissions off, the default (ADR 2026-10-29-configurable-access-features): the roles
 * routes are gone, the gates decide everything, and staff share {@code ['*']}.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
})
class RolesPermissionsSwitchedOffTest {

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private TicketService tickets;
    @Autowired private SettingsService settings;

    @Test
    void theRolesRoutesAnswer404AndTheFeatureIsSharedOff() throws Exception {
        AgentProfile admin = staff(agents, "admin", true, null);
        for (String path : new String[] {"/escalated/admin/roles", "/escalated/admin/roles/create",
                "/escalated/admin/roles/1/edit", "/escalated/api/admin/roles", "/escalated/api/admin/roles/permissions"}) {
            mvc.perform(get(path).with(user(admin.getEmail()))).andExpect(status().isNotFound());
        }
        mvc.perform(page(post("/escalated/admin/roles"), admin, "{\"name\":\"x\"}")).andExpect(status().isNotFound());
        // Not even the gate is reached: the route does not exist.
        mvc.perform(get("/escalated/admin/roles")).andExpect(status().isNotFound());

        JsonNode features = shared(mvc, "/escalated/admin/settings", admin).get("features");
        assertThat(features.get("roles_permissions").asBoolean()).isFalse();
        assertThat(features.get("two_factor").asBoolean()).isFalse();
        assertThat(features.get("sso").asBoolean()).isFalse();
    }

    @Test
    void theRequestsThatNeedAPermissionWhenOnPassTheGatesAlone() throws Exception {
        AgentProfile admin = staff(agents, "admin", true, null);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Ticket ticket = tickets.create("Off " + unique, "b", "R", "r-" + unique + "@example.com",
                TicketPriority.HIGH, null);
        mvc.perform(get("/escalated/admin/settings").with(user(admin.getEmail()))).andExpect(status().isOk());
        mvc.perform(api(post("/escalated/api/admin/departments"), admin, "{\"name\":\"Off " + unique + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(page(post("/escalated/agent/tickets/" + ticket.getTicketNumber() + "/status"), admin,
                "{\"status\":\"closed\"}")).andExpect(status().isSeeOther());

        assertThat(shared(mvc, "/escalated/admin/settings", admin).get("permissions").toString()).isEqualTo("[\"*\"]");
        AgentProfile agent = staff(agents, "agent", false, null);
        assertThat(shared(mvc, "/escalated/agent", agent).get("permissions").toString()).isEqualTo("[\"*\"]");
        // The gates still decide: an agent is not an admin.
        mvc.perform(get("/escalated/admin/settings").with(user(agent.getEmail()))).andExpect(status().isForbidden());
    }

    @Test
    void aSettingWithTheSwitchsNameCannotTurnItOn() throws Exception {
        AgentProfile admin = staff(agents, "admin", true, null);
        mvc.perform(api(post("/escalated/api/admin/settings"), admin,
                "{\"key\":\"roles_permissions\",\"value\":\"true\"}")).andExpect(status().isOk());
        settings.set("access.roles_permissions", "true", "general");
        mvc.perform(get("/escalated/api/admin/roles").with(user(admin.getEmail()))).andExpect(status().isNotFound());
        assertThat(shared(mvc, "/escalated/admin/settings", admin).get("features").get("roles_permissions").asBoolean())
                .isFalse();
    }
}
