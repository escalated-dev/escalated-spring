package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.escalated.models.AgentProfile;
import embedhost.EmbedHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code escalated.ui.embed.replace-admin-gate}: the host's chain and middleware alone decide who
 * reaches the embedded admin. Only there: the agent panel and the shared {@code is_admin} keep
 * Escalated's gates.
 */
class EmbeddedAdminHostGateTest extends EmbedTestSupport {

    @BeforeEach
    void replaceTheGate() {
        properties.getUi().getEmbed().setReplaceAdminGate(true);
    }

    @Test
    void aUserTheHostLetsThroughReachesTheAdmin() throws Exception {
        AgentProfile agent = profile("agent", true, false);
        mvc.perform(get("/admin/support/tickets").session(signedIn(agent.getEmail())).with(INERTIA))
                .andExpect(status().isOk())
                .andExpect(header().string(EmbedHost.MIDDLEWARE_HEADER, "ran /admin/support/tickets"));
        JsonNode escalated = page("/admin/support/settings", agent).get("props").get("escalated");
        assertThat(escalated.get("navigation")).as("built for any signed-in user").isNotEmpty();
        assertThat(escalated.get("is_admin").asBoolean()).as("Escalated's own gate is unchanged").isFalse();
    }

    @Test
    void theHostMiddlewareDecides() throws Exception {
        AgentProfile blocked = profile("blocked", true, true);
        mvc.perform(get("/admin/support/tickets").session(signedIn(blocked.getEmail())).with(INERTIA))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/support/tickets").with(INERTIA)).andExpect(status().isForbidden());
    }

    @Test
    void theAgentPanelKeepsEscalatedsGates() throws Exception {
        AgentProfile hostUser = profile("someone", false, false);
        mvc.perform(get("/escalated/agent/tickets").session(signedIn(hostUser.getEmail())).with(INERTIA))
                .andExpect(status().isForbidden());
    }
}
