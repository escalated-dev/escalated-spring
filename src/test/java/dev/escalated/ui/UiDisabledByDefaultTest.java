package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import hostapp.HostApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Without {@code escalated.ui.enabled} a host is API-only, as before: no pages, no Inertia beans. */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class UiDisabledByDefaultTest {

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;
    @Autowired private AgentProfileRepository agents;

    @Test
    void noPageIsServedAndNoInertiaBeanExists() throws Exception {
        AgentProfile agent = new AgentProfile();
        agent.setName("Agent");
        agent.setEmail("agent-" + UUID.randomUUID() + "@example.com");
        agent.setAgent(true);
        agents.save(agent);

        mvc.perform(get("/escalated/agent").with(user(agent.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(get("/escalated/agent/tickets").with(user(agent.getEmail()))).andExpect(status().isNotFound());
        assertThat(context.getBeansOfType(Inertia.class)).isEmpty();
        assertThat(context.getBeansOfType(EscalatedUiRenderer.class)).isEmpty();
    }
}
