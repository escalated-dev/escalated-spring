package dev.escalated.ui;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code escalated.routes.customer-middleware}: the customer pages, the customer ticket API and the
 * customer knowledge base take the configured guards on top of being signed in. Here
 * {@code escalated-agent} (Escalated's agent gate, which makes the portal staff-only) and a host
 * {@code AuthorizationManager} bean that turns away one blocked address.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CustomerMiddlewareTest.HostGuards.class)
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
    "escalated.knowledge-base.enabled=true",
    "escalated.routes.customer-middleware=escalated-agent,notBlocked",
})
class CustomerMiddlewareTest {

    static final String BLOCKED = "blocked-agent@example.com";

    @TestConfiguration
    static class HostGuards {
        @Bean
        AuthorizationManager<RequestAuthorizationContext> notBlocked() {
            return (authentication, context) ->
                    new AuthorizationDecision(!BLOCKED.equals(authentication.get().getName()));
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private TicketService tickets;

    private String unique;
    private AgentProfile agent;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent = agents.save(agent);
        if (agents.findByEmail(BLOCKED).isEmpty()) {
            AgentProfile blocked = new AgentProfile();
            blocked.setName("Blocked");
            blocked.setEmail(BLOCKED);
            blocked.setAgent(true);
            agents.save(blocked);
        }
    }

    @Test
    void aSignedInUserWhoFailsTheGuardIsRefusedEverywhereOnThePortal() throws Exception {
        String customer = "customer-" + unique + "@example.com";
        Ticket ticket = tickets.create("Help " + unique, "Please.", "Casey", customer, TicketPriority.LOW, null);

        mvc.perform(page(get("/escalated")).with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(page(get("/escalated/create")).with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(page(get("/escalated/" + ticket.getTicketNumber())).with(user(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(page(post("/escalated/" + ticket.getTicketNumber() + "/reply")).with(user(customer))
                        .with(UiTestSupport.xsrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(page(get("/escalated/kb")).with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(page(get("/escalated/kb/some-article")).with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/escalated/api/customer/tickets").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(post("/escalated/api/customer/tickets").with(user(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"S\",\"description\":\"D\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aHostGuardIsAppliedToo() throws Exception {
        mvc.perform(page(get("/escalated")).with(user(BLOCKED))).andExpect(status().isForbidden());
        mvc.perform(get("/escalated/api/customer/tickets").with(user(BLOCKED))).andExpect(status().isForbidden());
    }

    @Test
    void someoneWhoPassesEveryGuardGetsIn() throws Exception {
        mvc.perform(page(get("/escalated")).with(user(agent.getEmail()))).andExpect(status().isOk());
        mvc.perform(page(get("/escalated/create")).with(user(agent.getEmail()))).andExpect(status().isOk());
        mvc.perform(page(get("/escalated/kb")).with(user(agent.getEmail()))).andExpect(status().isOk());
        mvc.perform(get("/escalated/api/customer/tickets").with(user(agent.getEmail()))).andExpect(status().isOk());
    }

    @Test
    void theKnowledgeBaseIsNoLongerPublic() throws Exception {
        int status = mvc.perform(page(get("/escalated/kb"))).andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isIn(401, 403, 302);
    }

    @Test
    void theStaffPagesAreUnaffected() throws Exception {
        mvc.perform(page(get("/escalated/agent/tickets")).with(user(agent.getEmail()))).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder page(MockHttpServletRequestBuilder request) {
        return request.header("X-Inertia", "true").header("X-Inertia-Version", "build-1");
    }
}
