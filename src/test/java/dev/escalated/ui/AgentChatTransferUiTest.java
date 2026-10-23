package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.ChatSessionService;
import hostapp.HostApplication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Transfer from the agent ticket page: {@code route('escalated.admin.chat.transfer', chat_session_id)}
 * POSTed with {@code {agent_id}} or {@code {department_id}}, the csrf-token meta tag and the agent's
 * browser session, as the shared frontend's ChatActionBar does.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
})
class AgentChatTransferUiTest {

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private ChatSessionService chats;
    @Autowired private UiRoutes routes;

    private AgentProfile from;
    private AgentProfile to;
    private Long sessionId;

    @BeforeEach
    void seed() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        from = agent("From " + unique);
        to = agent("To " + unique);
        sessionId = chats.startForWidget("Visitor", "visitor-" + unique + "@example.com", "Hello?", null, null)
                .session().getId();
        if ("waiting".equals(chats.findById(sessionId).getStatus())) {
            chats.accept(sessionId, from.getId());
        }
    }

    @Test
    void theRouteNameResolvesToTheUiEndpoint() {
        assertThat(routes.url("escalated.admin.chat.transfer", sessionId))
                .isEqualTo("/escalated/agent/chat/" + sessionId + "/transfer");
        assertThat(routes.script()).contains("escalated.admin.chat.transfer");
    }

    @Test
    void anAgentsBrowserSessionTransfersTheChatThroughTheNamedRoute() throws Exception {
        transfer(sessionId, "{\"agent_id\":" + to.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Chat transferred."));

        assertThat(chats.findById(sessionId).getAgentId()).isEqualTo(to.getId());
    }

    @Test
    void refusalsAre422And404() throws Exception {
        transfer(sessionId, "{}").andExpect(status().isUnprocessableEntity());
        transfer(Long.MAX_VALUE, "{\"agent_id\":" + to.getId() + "}").andExpect(status().isNotFound());
        assertThat(chats.findById(sessionId).getAgentId()).isEqualTo(from.getId());
    }

    @Test
    void withoutTheCsrfTokenOrAsANonAgentItIsRefused() throws Exception {
        mvc.perform(post(routes.url("escalated.admin.chat.transfer", sessionId)).session(agentSession())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"agent_id\":" + to.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post(routes.url("escalated.admin.chat.transfer", sessionId)))
                        .with(user("customer-" + UUID.randomUUID() + "@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"agent_id\":" + to.getId() + "}"))
                .andExpect(status().isForbidden());
        assertThat(chats.findById(sessionId).getAgentId()).isEqualTo(from.getId());
    }

    private ResultActions transfer(Long id, String body) throws Exception {
        return mvc.perform(withCsrf(post(routes.url("escalated.admin.chat.transfer", id))).session(agentSession())
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private AgentProfile agent(String name) {
        AgentProfile agent = new AgentProfile();
        agent.setName(name);
        agent.setEmail("chat-agent-" + UUID.randomUUID() + "@example.com");
        agent.setAgent(true);
        return agents.save(agent);
    }

    /** The csrf-token meta tag's value sent back as X-CSRF-TOKEN, with its cookie. */
    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        String token = UUID.randomUUID().toString();
        return request.cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", token)).header("X-CSRF-TOKEN", token);
    }

    /** A signed-in browser session: the security context stored in the HTTP session. */
    private MockHttpSession agentSession() {
        MockHttpSession session = new MockHttpSession();
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                from.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }
}
