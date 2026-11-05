package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.escalated.models.AgentProfile;
import embedhost.EmbedHost;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Layout mode (host types A and B): the admin answers under the host's prefix, behind the host's
 * chain and middleware and still behind Escalated's own guards, with the same pages and route
 * names; the old URLs are gone; everything that is not the admin stays where it was.
 */
class EmbeddedAdminLayoutTest extends EmbedTestSupport {

    @Autowired private UiRoutes routes;

    @Test
    void theAdminAnswersUnderTheHostPrefixThroughTheHostMiddleware() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        MvcResult result = mvc.perform(get("/admin/support/tickets").queryParam("status", "open")
                        .session(signedIn(admin.getEmail())).with(INERTIA))
                .andExpect(status().isOk())
                .andExpect(header().string(EmbedHost.MIDDLEWARE_HEADER, "ran /admin/support/tickets"))
                .andReturn();
        assertThat(result.getResponse().getHeaders("Content-Security-Policy"))
                .as("layout mode adds no frame header").noneMatch(value -> value.contains("frame-ancestors"));

        JsonNode page = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("component").asText()).isEqualTo("Escalated/Admin/Tickets/Index");
        assertThat(page.get("url").asText()).isEqualTo("/admin/support/tickets?status=open");

        JsonNode embed = page.get("props").get("escalated").get("embed");
        assertThat(fieldNames(embed)).containsExactly("enabled", "mode", "layout", "route", "base_url", "allowed_origins");
        assertThat(embed.get("enabled").asBoolean()).isTrue();
        assertThat(embed.get("mode").asText()).isEqualTo("layout");
        assertThat(embed.get("layout").asText()).isEqualTo("Layouts/AdminLayout");
        assertThat(embed.get("route").asText()).isEqualTo("escalated.admin.tickets.index");
        assertThat(embed.get("base_url").asText()).isEqualTo("/admin/support");
        assertThat(embed.get("allowed_origins").isArray()).isTrue();
        assertThat(embed.get("allowed_origins")).isEmpty();
    }

    @Test
    void theStandaloneAdminUrlsAreGone() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        for (String path : List.of("/escalated/admin/tickets", "/escalated/admin/settings", "/escalated/admin/plugins")) {
            mvc.perform(get(path).session(signedIn(admin.getEmail())).with(INERTIA))
                    .andExpect(status().isNotFound());
        }
        // Not even to anyone else.
        mvc.perform(get("/escalated/admin/tickets")).andExpect(status().isNotFound());
    }

    @Test
    void theHostMiddlewareCanRefuse() throws Exception {
        AgentProfile blocked = profile("blocked", true, true);
        mvc.perform(get("/admin/support/tickets").session(signedIn(blocked.getEmail())).with(INERTIA))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(EmbedHost.MIDDLEWARE_HEADER));
        // And the host's chain turns away anyone not signed in, before anything of Escalated's runs.
        mvc.perform(get("/admin/support/tickets").with(INERTIA)).andExpect(status().isForbidden());
    }

    @Test
    void escalatedsAdminGateStillRefusesANonAdmin() throws Exception {
        AgentProfile agent = profile("agent", true, false);
        mvc.perform(get("/admin/support/tickets").session(signedIn(agent.getEmail())).with(INERTIA))
                .andExpect(status().isForbidden())
                .andExpect(header().string(EmbedHost.MIDDLEWARE_HEADER, "ran /admin/support/tickets"));
        AgentProfile hostUser = profile("someone", false, false);
        mvc.perform(get("/admin/support/settings").session(signedIn(hostUser.getEmail())).with(INERTIA))
                .andExpect(status().isForbidden());
    }

    @Test
    void routeNamesFollowTheMove() {
        assertThat(routes.url("escalated.admin.tickets.index")).isEqualTo("/admin/support/tickets");
        assertThat(routes.url("escalated.admin.tickets.show", 7)).isEqualTo("/admin/support/tickets/7");
        assertThat(routes.url("escalated.admin.plugins.activate", "acme--billing"))
                .isEqualTo("/admin/support/plugins/acme--billing/activate");
        assertThat(routes.url("escalated.admin.api-tokens.index")).isEqualTo("/admin/support/api-tokens");
        // Not the admin: unchanged.
        assertThat(routes.url("escalated.admin.two-factor.index")).isEqualTo("/escalated/admin/settings/two-factor");
        assertThat(routes.url("escalated.two-factor.challenge")).isEqualTo("/escalated/two-factor/challenge");
        assertThat(routes.url("escalated.agent.dashboard")).isEqualTo("/escalated/agent");
        assertThat(routes.url("escalated.admin.chat.end", 3)).isEqualTo("/escalated/agent/chat/3/end");
        assertThat(routes.url("escalated.customer.kb.index")).isEqualTo("/escalated/kb");
        assertThat(routes.url("escalated.guest.tickets.create")).isEqualTo("/escalated/guest/create");
        assertThat(routes.script()).contains("\"escalated.admin.settings\":\"/admin/support/settings\"");
    }

    @Test
    void writesRedirectUnderThePrefixAndStillNeedTheCsrfToken() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        mvc.perform(post("/admin/support/tags").session(signedIn(admin.getEmail())).with(INERTIA).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Embedded " + unique + "\"}"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/admin/support/tags"));
        // The host leaves CSRF on these pages to Escalated's chain, which still checks it.
        mvc.perform(post("/admin/support/tags").session(signedIn(admin.getEmail())).with(INERTIA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"No token " + unique + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theVersionCheckSendsTheClientBackToTheEmbeddedUrl() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        mvc.perform(get("/admin/support/tickets").session(signedIn(admin.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "stale"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Inertia-Location", "http://localhost/admin/support/tickets"));
    }

    @Test
    void anInertiaErrorUnderThePrefixIsTheErrorPage() throws Exception {
        AgentProfile agent = profile("agent", true, false);
        JsonNode page = JSON.readTree(mvc.perform(get("/admin/support/tickets")
                        .session(signedIn(agent.getEmail())).with(INERTIA))
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString());
        assertThat(page.get("component").asText()).isEqualTo(InertiaErrorPageFilter.PAGE);
        assertThat(page.get("url").asText()).isEqualTo("/admin/support/tickets");
    }

    @Test
    void whatIsNotTheAdminStaysAndSharesEmbedAsOff() throws Exception {
        AgentProfile admin = profile("admin", true, true);
        JsonNode page = page("/escalated/agent/tickets", admin);
        assertThat(page.get("component").asText()).startsWith("Escalated/Agent/");
        JsonNode embed = page.get("props").get("escalated").get("embed");
        assertThat(embed.get("enabled").asBoolean()).isFalse();
        assertThat(embed.get("route").isNull()).isTrue();
        assertThat(embed.get("base_url").asText()).isEqualTo("/admin/support");
        // The admin menu is shared on every page, so a host sidebar can list it anywhere.
        assertThat(page.get("props").get("escalated").get("navigation")).isNotEmpty();

        AgentProfile agent = profile("agent", true, false);
        JsonNode agentPage = page("/escalated/agent/tickets", agent);
        assertThat(agentPage.get("props").get("escalated").get("navigation")).as("not an admin").isEmpty();

        // Two-factor enrolment is every staff user's own: it does not move under the admin prefix.
        mvc.perform(get("/admin/support/settings/two-factor").session(signedIn(admin.getEmail())).with(INERTIA))
                .andExpect(status().isNotFound());
        // The JSON admin API does not move either.
        mvc.perform(get("/escalated/api/admin/departments").with(user(admin.getEmail())))
                .andExpect(status().isOk());
    }

    static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
