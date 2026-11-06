package dev.escalated.ui;

import static dev.escalated.ui.EmbeddedAdminLayoutTest.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Permission;
import dev.escalated.models.Role;
import dev.escalated.plugins.PluginAdmin;
import dev.escalated.plugins.PluginRegistry;
import dev.escalated.repositories.PermissionRepository;
import dev.escalated.repositories.PluginActivationRepository;
import dev.escalated.repositories.RoleRepository;
import dev.escalated.services.PermissionSeeder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@code escalated.navigation} and {@link AdminNavigation#adminNavigation}: the item shape, the
 * standalone sidebar's order, {@code active}, and the server-side filtering (admin gate, pages this
 * host serves, feature switches, route permissions, plugin admin items).
 */
class EmbeddedAdminNavigationTest extends EmbedTestSupport {

    private static final List<String> CORE = List.of(
            "reports", "tickets", "departments", "sla-policies", "knowledge-base", "escalation-rules", "workflows",
            "automations", "webhooks", "tags", "custom-fields", "statuses", "canned-responses", "macros", "skills",
            "users", "capacity", "business-hours", "audit-log", "api-tokens", "custom-objects", "plugins", "import",
            "settings");

    @Autowired private AdminNavigation navigation;
    @Autowired private PermissionSeeder seeder;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private PluginRegistry plugins;
    @Autowired private PluginActivationRepository activations;

    private static List<String> keys(JsonNode navigation) {
        List<String> keys = new ArrayList<>();
        navigation.forEach(item -> keys.add(item.get("key").asText()));
        return keys;
    }

    @Test
    void theMenuHasTheStandaloneSidebarsItemsInItsOrder() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        JsonNode nav = page("/admin/support/departments", admin).get("props").get("escalated").get("navigation");

        // Newsletters: this host's newsletter admin is an API, with no page to link. Roles: the
        // roles and permissions switch is off.
        assertThat(keys(nav)).containsExactlyElementsOf(CORE);
        JsonNode departments = nav.get(2);
        assertThat(fieldNames(departments)).containsExactly(
                "key", "label", "route", "url", "icon", "position", "active", "badge", "plugin", "children");
        assertThat(departments.get("label").asText()).isEqualTo("Departments");
        assertThat(departments.get("route").asText()).isEqualTo("escalated.admin.departments.index");
        assertThat(departments.get("url").asText()).isEqualTo("/admin/support/departments");
        assertThat(departments.get("icon").asText()).isEqualTo("departments");
        assertThat(departments.get("position").asInt()).isEqualTo(30);
        assertThat(departments.get("badge").isNull()).isTrue();
        assertThat(departments.get("plugin").isNull()).isTrue();
        assertThat(departments.get("children")).isEmpty();

        List<String> active = new ArrayList<>();
        nav.forEach(item -> {
            if (item.get("active").asBoolean()) {
                active.add(item.get("key").asText());
            }
        });
        assertThat(active).containsExactly("departments");
        assertThat(nav.get(7).get("position").asDouble()).isEqualTo(53.5);

        // Below an item's page counts as that item.
        JsonNode below = page("/admin/support/departments/create", admin).get("props").get("escalated").get("navigation");
        assertThat(below.get(2).get("active").asBoolean()).isTrue();
    }

    @Test
    void rolesAndPermissionsFilterTheMenu() throws Exception {
        properties.getAccess().setRolesPermissions(true);
        seeder.seed();
        Role tickets = new Role();
        tickets.setName("embed-nav-" + unique);
        tickets.setPermissions(new HashSet<>());
        for (String slug : List.of("ticket.view", "role.view")) {
            Permission permission = permissions.findByName(slug).orElseThrow();
            tickets.getPermissions().add(permission);
        }
        tickets = roles.save(tickets);
        AgentProfile admin = profile("admin", true, true);
        admin.setRole(tickets);
        admin = agents.save(admin);

        List<String> keys = keysFor(admin);
        assertThat(keys).as("lacks settings.view").doesNotContain("settings");
        assertThat(keys).as("the switch is on, and role.view is held").contains("tickets", "roles");
        assertThat(keys).doesNotContain("departments", "webhooks");

        AgentProfile full = profile("full", true, true);
        full.setRole(roles.findByName("admin").orElseThrow());
        full = agents.save(full);
        assertThat(keysFor(full)).contains("settings", "roles", "tickets");
    }

    private List<String> keysFor(AgentProfile who) {
        List<String> keys = new ArrayList<>();
        for (Map<String, Object> item : navigation.adminNavigation(signedInAs(who), new MockHttpServletRequest())) {
            keys.add((String) item.get("key"));
        }
        return keys;
    }

    @Test
    void activePluginsAdminItemsAreMergedByPosition() throws Exception {
        activations.deleteAll();
        AgentProfile admin = profile("admin", true, true);
        assertThat(PluginAdmin.apply(plugins, "acme--billing", PluginAdmin.Action.ACTIVATE, signedInAs(admin)).success())
                .isTrue();
        try {
            JsonNode escalated = page("/admin/support/plugins/acme--billing/settings", admin).get("props").get("escalated");
            JsonNode nav = escalated.get("navigation");
            List<String> keys = keys(nav);
            assertThat(keys).contains("plugin:acme--billing:0");
            // Position 30, after Departments (also 30) and before SLA Policies (40).
            assertThat(keys.indexOf("plugin:acme--billing:0")).isEqualTo(keys.indexOf("departments") + 1);
            JsonNode billing = nav.get(keys.indexOf("plugin:acme--billing:0"));
            assertThat(billing.get("label").asText()).isEqualTo("Billing");
            assertThat(billing.get("route").isNull()).isTrue();
            assertThat(billing.get("url").asText()).isEqualTo("/admin/support/plugins/acme--billing/settings");
            assertThat(billing.get("icon").asText()).isEqualTo("credit-card");
            assertThat(billing.get("plugin").asText()).isEqualTo("acme--billing");
            assertThat(billing.get("active").asBoolean()).isTrue();
            // The agent-section item is not an admin item; the capability-guarded one is not granted.
            assertThat(keys).doesNotContain("plugin:acme--billing:1");

            // The plugin's own menu share follows the move too, and the page answers there.
            assertThat(escalated.get("plugins").get("menuItems").get(0).get("route").asText())
                    .isEqualTo("/admin/support/plugins/acme--billing/settings");
            mvc.perform(get("/admin/support/plugins/acme--billing/settings").session(signedIn(admin.getEmail()))
                            .with(INERTIA))
                    .andExpect(status().isOk());
        } finally {
            activations.deleteAll();
        }
    }

    @Test
    void nobodyButAnAdminGetsAMenu() {
        AgentProfile agent = profile("agent", true, false);
        assertThat(navigation.adminNavigation(signedInAs(agent), new MockHttpServletRequest())).isEmpty();
        assertThat(navigation.adminNavigation(null, new MockHttpServletRequest())).isEmpty();
    }

    @Test
    void theHelperReadsTheSignedInUserForAHostPage() {
        AgentProfile admin = profile("admin", true, true);
        SecurityContextHolder.getContext().setAuthentication(signedInAs(admin));
        try {
            MockHttpServletRequest hostPage = new MockHttpServletRequest("GET", "/admin/orders");
            List<Map<String, Object>> items = navigation.adminNavigation(hostPage);
            assertThat(items).extracting(item -> item.get("url")).contains("/admin/support/tickets");
            assertThat(items).noneMatch(item -> Boolean.TRUE.equals(item.get("active")));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static UsernamePasswordAuthenticationToken signedInAs(AgentProfile who) {
        return UsernamePasswordAuthenticationToken.authenticated(who.getEmail(), null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
}
