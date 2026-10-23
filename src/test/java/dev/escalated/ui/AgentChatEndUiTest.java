package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.ChatSession;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End Chat on the agent ticket page: {@code route('escalated.admin.chat.end', chat_session_id)}
 * POSTed with {@code {}}, the csrf-token meta tag and the agent's browser session, as the shared
 * frontend's ChatActionBar does.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
})
class AgentChatEndUiTest {

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private ChatSessionService chats;
    @Autowired private UiRoutes routes;

    private AgentProfile agent;
    private Long sessionId;

    @BeforeEach
    void seed() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("chat-agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent = agents.save(agent);
        sessionId = chats.startForWidget("Visitor", "visitor-" + unique + "@example.com", "Hello?", null, null)
                .session().getId();
    }

    @Test
    void theRouteNameResolvesToTheUiEndpoint() {
        assertThat(routes.url("escalated.admin.chat.end", sessionId))
                .isEqualTo("/escalated/agent/chat/" + sessionId + "/end");
        assertThat(routes.script()).contains("escalated.admin.chat.end");
    }

    @Test
    void anAgentsBrowserSessionEndsTheChatThroughTheNamedRoute() throws Exception {
        endChat(routes.url("escalated.admin.chat.end", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Chat ended."));

        ChatSession ended = chats.findById(sessionId);
        assertThat(ended.getStatus()).isEqualTo("ended");
        assertThat(ended.getEndedAt()).isNotNull();
    }

    @Test
    void anAlreadyEndedChatIsA422() throws Exception {
        chats.end(sessionId);

        endChat(routes.url("escalated.admin.chat.end", sessionId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Chat is already ended."));
    }

    @Test
    void anUnknownSessionIsA404() throws Exception {
        endChat(routes.url("escalated.admin.chat.end", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void withoutTheCsrfTokenItIsRefused() throws Exception {
        mvc.perform(post(routes.url("escalated.admin.chat.end", sessionId)).session(agentSession())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        assertThat(chats.findById(sessionId).getStatus()).isNotEqualTo("ended");
    }

    @Test
    void someoneWhoIsNotAnAgentCannotEndAChat() throws Exception {
        mvc.perform(withCsrf(post(routes.url("escalated.admin.chat.end", sessionId)))
                        .with(user("customer-" + UUID.randomUUID() + "@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        assertThat(chats.findById(sessionId).getStatus()).isNotEqualTo("ended");
    }

    @Test
    void theStatelessApiEndpointDoesNotAcceptTheBrowserSessionButStillWorksForApiCallers() throws Exception {
        // Why the page needs the UI route: /escalated/api/** ignores the session cookie.
        mvc.perform(post("/escalated/api/agent/chat/{id}/end", sessionId).session(agentSession())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
        assertThat(chats.findById(sessionId).getStatus()).isNotEqualTo("ended");

        // The existing API shape is unchanged for an authenticated caller.
        mvc.perform(post("/escalated/api/agent/chat/{id}/end", sessionId).with(user(agent.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ended"));
    }

    private org.springframework.test.web.servlet.ResultActions endChat(String url) throws Exception {
        return mvc.perform(withCsrf(post(url)).session(agentSession())
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
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
                agent.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }
}
