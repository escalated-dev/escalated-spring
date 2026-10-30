package dev.escalated.security;

import static dev.escalated.security.AccessTestSupport.api;
import static dev.escalated.security.AccessTestSupport.page;
import static dev.escalated.security.AccessTestSupport.role;
import static dev.escalated.security.AccessTestSupport.shared;
import static dev.escalated.security.AccessTestSupport.staff;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Role;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.PermissionRepository;
import dev.escalated.repositories.RoleRepository;
import dev.escalated.services.ApiTokenService;
import dev.escalated.services.PermissionSeeder;
import dev.escalated.services.SettingsService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Roles and permissions switched on (ADR 2026-10-29-configurable-access-features, port test
 * checklist), through the real security chains and router. The switch is read on every request, so
 * it is turned on here at runtime, in the context the other UI tests share.
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
class RolesPermissionsEnforcementTest {

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private PermissionSeeder seeder;
    @Autowired private TicketService tickets;
    @Autowired private ApiTokenService apiTokens;
    @Autowired private SettingsService settings;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private Role adminRole;
    private Role agentRole;
    private Role lightAgent;
    private Ticket ticket;

    @Autowired private dev.escalated.config.EscalatedProperties properties;

    @org.junit.jupiter.api.AfterEach
    void switchOff() {
        properties.getAccess().setRolesPermissions(null);
    }

    @BeforeEach
    void seed() {
        // Switched at runtime rather than by a property, so this shares the UI tests' cached context.
        properties.getAccess().setRolesPermissions(true);
        seeder.seed();
        adminRole = roles.findByName("admin").orElseThrow();
        agentRole = roles.findByName("agent").orElseThrow();
        lightAgent = roles.findByName("light_agent").orElseThrow();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        ticket = tickets.create("Printer on fire " + unique, "It is on fire.", "Requester",
                "requester-" + unique + "@example.com", TicketPriority.HIGH, null);
    }

    private String agentTicket() {
        return "/escalated/agent/tickets/" + ticket.getTicketNumber();
    }

    @Test
    void theSwitchIsSharedAndTheRolesRoutesWork() throws Exception {
        AgentProfile admin = staff(agents, "admin", true, adminRole);
        JsonNode escalated = shared(mvc, "/escalated/admin/roles", admin);
        assertThat(escalated.get("features").get("roles_permissions").asBoolean()).isTrue();
        assertThat(escalated.get("features").get("two_factor").asBoolean()).isFalse();
        assertThat(escalated.get("features").get("sso").asBoolean()).isFalse();
        mvc.perform(get("/escalated/admin/roles").with(user(admin.getEmail()))).andExpect(status().isOk());
        mvc.perform(get("/escalated/api/admin/roles").with(user(admin.getEmail()))).andExpect(status().isOk());

        // The user's own slugs, not ['*'].
        List<String> slugs = new ArrayList<>();
        escalated.get("permissions").forEach(slug -> slugs.add(slug.asText()));
        assertThat(slugs).contains("ticket.view", "settings.manage", "role.manage").doesNotContain("*");

        AgentProfile light = staff(agents, "light", false, lightAgent);
        List<String> lightSlugs = new ArrayList<>();
        shared(mvc, "/escalated/agent", light).get("permissions").forEach(slug -> lightSlugs.add(slug.asText()));
        assertThat(lightSlugs).containsExactlyInAnyOrder(
                "ticket.view", "reply.create", "reply.create_internal", "kb.view", "macro.view", "tag.view");
    }

    @Test
    void passingTheAdminGateWithoutARoleGrantsNothing() throws Exception {
        AgentProfile admin = staff(agents, "admin", true, null);
        // Admin read, admin write, and an agent ticket action.
        mvc.perform(get("/escalated/admin/settings").with(user(admin.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(api(post("/escalated/api/admin/departments"), admin, "{\"name\":\"Ops\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(page(post(agentTicket() + "/priority"), admin, "{\"priority\":\"low\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRoleGrantingThePermissionLetsTheSameRequestsThrough() throws Exception {
        AgentProfile admin = staff(agents, "admin", true, adminRole);
        mvc.perform(get("/escalated/admin/settings").with(user(admin.getEmail()))).andExpect(status().isOk());
        mvc.perform(api(post("/escalated/api/admin/departments"), admin, "{\"name\":\"Ops " + admin.getId() + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(page(post(agentTicket() + "/priority"), admin, "{\"priority\":\"low\"}"))
                .andExpect(status().isSeeOther());

        // A role with only the read: the read works, the write does not.
        AgentProfile reader = staff(agents, "reader", true, role(roles, permissions, "department.view"));
        mvc.perform(api(get("/escalated/api/admin/departments"), reader, "")).andExpect(status().isOk());
        mvc.perform(api(post("/escalated/api/admin/departments"), reader, "{\"name\":\"No\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aLightAgentViewsAndNotesButCannotAssignReprioritiseCloseOrDelete() throws Exception {
        // Also an admin, so the gates pass everywhere and only the role decides.
        AgentProfile light = staff(agents, "light", true, lightAgent);
        AgentProfile other = staff(agents, "other", false, agentRole);

        mvc.perform(get(agentTicket()).with(user(light.getEmail()))).andExpect(status().isOk());
        mvc.perform(page(post(agentTicket() + "/note"), light, "{\"body\":\"Looking\"}"))
                .andExpect(status().isSeeOther());

        mvc.perform(page(post(agentTicket() + "/assign"), light, "{\"agent_id\":" + other.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(page(post(agentTicket() + "/priority"), light, "{\"priority\":\"low\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(page(post(agentTicket() + "/status"), light, "{\"status\":\"closed\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(api(delete("/escalated/api/admin/tickets/" + ticket.getId()), light, ""))
                .andExpect(status().isForbidden());
        Ticket unchanged = tickets.findById(ticket.getId());
        assertThat(unchanged.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(unchanged.getStatus()).isNotEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void aClosingStatusChangeNeedsTicketClose() throws Exception {
        AgentProfile editor = staff(agents, "editor", false, role(roles, permissions, "ticket.view", "ticket.edit"));
        mvc.perform(page(post(agentTicket() + "/status"), editor, "{\"status\":\"in_progress\"}"))
                .andExpect(status().isSeeOther());
        for (String closing : new String[] {"closed", "resolved", "reopened"}) {
            mvc.perform(page(post(agentTicket() + "/status"), editor, "{\"status\":\"" + closing + "\"}"))
                    .andExpect(status().isForbidden());
        }
        // The JSON API decides the same way.
        AgentProfile apiEditor = staff(agents, "api-editor", true, role(roles, permissions, "ticket.view", "ticket.edit"));
        mvc.perform(api(post("/escalated/api/admin/tickets/" + ticket.getId() + "/status"), apiEditor,
                "{\"status\":\"resolved\"}")).andExpect(status().isForbidden());

        AgentProfile closer = staff(agents, "closer", false,
                role(roles, permissions, "ticket.view", "ticket.edit", "ticket.close"));
        mvc.perform(page(post(agentTicket() + "/status"), closer, "{\"status\":\"resolved\"}"))
                .andExpect(status().isSeeOther());
        assertThat(tickets.findById(ticket.getId()).getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void bulkActionsCheckEachActionsPermission() throws Exception {
        AgentProfile editor = staff(agents, "bulk", true, role(roles, permissions, "ticket.view", "ticket.edit"));
        String ids = "{\"ticket_ids\":[" + ticket.getId() + "],";
        mvc.perform(page(post("/escalated/admin/tickets/bulk"), editor, ids + "\"action\":\"priority\",\"value\":\"low\"}"))
                .andExpect(status().isSeeOther());
        assertThat(tickets.findById(ticket.getId()).getPriority()).isEqualTo(TicketPriority.LOW);
        mvc.perform(page(post("/escalated/admin/tickets/bulk"), editor,
                ids + "\"action\":\"assign\",\"value\":" + editor.getId() + "}")).andExpect(status().isForbidden());
        mvc.perform(page(post("/escalated/admin/tickets/bulk"), editor, ids + "\"action\":\"delete\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(page(post("/escalated/admin/tickets/bulk"), editor, ids + "\"action\":\"status\",\"value\":\"closed\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(page(post("/escalated/admin/tickets/bulk"), editor,
                ids + "\"action\":\"status\",\"value\":\"in_progress\"}")).andExpect(status().isSeeOther());
    }

    @Test
    void theStaffApiChecksTheTokenOwnersPermissions() throws Exception {
        AgentProfile owner = staff(agents, "owner", false, null);
        String token = (String) apiTokens.createToken("cli", owner.getId(), "*", null).get("plainTextToken");
        mvc.perform(get("/escalated/api/agent/tickets").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        owner.setRole(lightAgent);
        agents.save(owner);
        mvc.perform(get("/escalated/api/agent/tickets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // A light agent may add an internal note through the API, but not run a ticket action.
        mvc.perform(post("/escalated/api/agent/tickets/" + ticket.getId() + "/replies")
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"body\":\"Note\",\"internal\":true}"))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(post("/escalated/api/agent/tickets/" + ticket.getId() + "/links")
                        .header("Authorization", "Bearer " + token).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSettingWithTheSwitchsNameChangesNothing() throws Exception {
        settings.set("roles_permissions", "false", "general");
        settings.set("access.roles_permissions", "false", "general");
        AgentProfile none = staff(agents, "admin", true, null);
        mvc.perform(get("/escalated/admin/settings").with(user(none.getEmail()))).andExpect(status().isForbidden());
    }

    /** Every admin, agent and staff API route has an entry in the route-permission map. */
    @Test
    void everyStaffRouteIsMapped() {
        List<String> unmapped = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = mapping.getKey();
            RequestMethodsRequestCondition methods = info.getMethodsCondition();
            List<String> verbs = new ArrayList<>();
            methods.getMethods().forEach(method -> verbs.add(method.name()));
            if (verbs.isEmpty()) {
                verbs.addAll(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
            }
            for (String pattern : info.getPatternValues()) {
                String path = concrete(pattern);
                if (!RoutePermissions.isStaffPath(path)) {
                    continue;
                }
                for (String verb : verbs) {
                    checked++;
                    if (RoutePermissions.mapped(verb, path) == null) {
                        unmapped.add(verb + " " + pattern);
                    }
                }
            }
        }
        assertThat(checked).as("staff routes found").isGreaterThan(200);
        assertThat(unmapped).as("staff routes missing from RoutePermissions").isEmpty();
    }

    /** {@code /a/{id:\d+}/b/**} as a request path: {@code /a/1/b/x/y}. */
    static String concrete(String pattern) {
        return pattern.replaceAll("\\{[^}/]+}", "1").replace("/**", "/x/y");
    }
}
