package dev.escalated.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.ApiToken;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ApiTokenRepository;
import dev.escalated.services.ApiTokenService;
import dev.escalated.services.email.inbound.InboundEmailService;
import dev.escalated.services.email.inbound.InboundEmailService.Outcome;
import dev.escalated.services.email.inbound.InboundEmailService.ProcessResult;
import dev.escalated.services.email.inbound.InboundMessage;
import hostapp.HostApplication;
import hostroutes.HostRoutes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The security wiring as a host actually boots it: auto-configuration, the
 * library's filter chains and the servlet filters all live.
 *
 * <p>Every controller test in the package runs with
 * {@code @AutoConfigureMockMvc(addFilters = false)}, so none of them has ever
 * seen a request go through the filter chain. That is how inbound email came to
 * be blocked by CSRF, how the admin API came to accept any logged-in user, and
 * how a valid API token came to be answered with 401 — all invisible to a suite
 * that switches the filters off.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(HostRoutes.class)
@TestPropertySource(
        properties = {
            // The package's mail service needs a JavaMailSender to construct.
            "spring.mail.host=localhost",
            "escalated.email.inbound-secret=" + SecurityWiringTest.INBOUND_SECRET,
        })
class SecurityWiringTest {

    static final String INBOUND_SECRET = "wiring-test-inbound-secret";

    @Autowired private MockMvc mockMvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private ApiTokenRepository tokens;
    @Autowired private ApiTokenService tokenService;

    @MockitoBean private InboundEmailService inboundService;

    private final List<Long> createdAgents = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long agentId : createdAgents) {
            tokens.deleteAll(tokens.findByAgentIdOrderByCreatedAtDesc(agentId));
            agents.deleteById(agentId);
        }
        createdAgents.clear();
    }

    @Nested
    class InboundEmail {

        @Test
        void aRequestCarryingTheSharedSecretReachesTheController() throws Exception {
            when(inboundService.process(any(InboundMessage.class)))
                    .thenReturn(new ProcessResult(Outcome.CREATED_NEW, 101L, null, List.of()));

            // A mail provider has no session, no CSRF token and no login. The
            // shared-secret header is the whole of its authentication.
            mockMvc.perform(post("/escalated/webhook/email/inbound")
                            .param("adapter", "postmark")
                            .header("X-Escalated-Inbound-Secret", INBOUND_SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "From": "alice@example.com",
                                        "To": "support@example.com",
                                        "Subject": "Help",
                                        "TextBody": "Broken widget"
                                    }
                                    """))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.outcome").value("created_new"));
        }

        @Test
        void aWrongSecretIsStillRejected() throws Exception {
            mockMvc.perform(post("/escalated/webhook/email/inbound")
                            .param("adapter", "postmark")
                            .header("X-Escalated-Inbound-Secret", "wrong")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(inboundService);
        }

        @Test
        void noSecretAtAllIsStillRejected() throws Exception {
            mockMvc.perform(post("/escalated/webhook/email/inbound")
                            .param("adapter", "postmark")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(inboundService);
        }
    }

    @Nested
    class AdminApi {

        @Test
        void anAnonymousRequestIsRejected() throws Exception {
            mockMvc.perform(get("/escalated/api/admin/users"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aLoggedInUserWhoIsNotAnAdminIsForbidden() throws Exception {
            // user() rather than @WithMockUser. The API chain is stateless, so
            // a @WithMockUser context never reaches it: the request arrives
            // anonymous and is refused for that reason alone -- which passes
            // on a build with no role check at all and proves nothing.
            mockMvc.perform(get("/escalated/api/admin/users")
                            .with(user("plain-user@host.test").roles("USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anAgentWhoIsNotAnAdminIsForbidden() throws Exception {
            AgentProfile agent = agent(false, true);

            mockMvc.perform(get("/escalated/api/admin/users").with(user(agent.getEmail())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anAdminIsAllowed() throws Exception {
            AgentProfile admin = agent(true, true);

            mockMvc.perform(get("/escalated/api/admin/users").with(user(admin.getEmail())))
                    .andExpect(status().isOk());
        }

        @Test
        void aDeactivatedAdminIsForbidden() throws Exception {
            AgentProfile admin = agent(true, true);
            admin.setActive(false);
            agents.save(admin);

            mockMvc.perform(get("/escalated/api/admin/users").with(user(admin.getEmail())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aHostUserGrantedTheEscalatedAdminRoleIsAllowed() throws Exception {
            // Hosts that keep roles in their own user store grant the authority
            // rather than flipping is_admin on a profile.
            mockMvc.perform(get("/escalated/api/admin/users")
                            .with(user("host-admin@host.test").roles("ESCALATED_ADMIN")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class AgentApi {

        @Test
        void aLoggedInUserWhoIsNotAnAgentIsForbidden() throws Exception {
            mockMvc.perform(get("/escalated/api/agent/tickets")
                            .with(user("customer@host.test").roles("USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anAgentIsAllowed() throws Exception {
            AgentProfile agent = agent(false, true);

            mockMvc.perform(get("/escalated/api/agent/tickets").with(user(agent.getEmail())))
                    .andExpect(status().isOk());
        }

        @Test
        void anAdminIsAllowedTheAgentApiToo() throws Exception {
            AgentProfile admin = agent(true, false);

            mockMvc.perform(get("/escalated/api/agent/tickets").with(user(admin.getEmail())))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class ApiTokens {

        @Test
        void aValidTokenAuthenticatesItsAgent() throws Exception {
            AgentProfile agent = agent(false, true);
            String token = tokenFor(agent);

            mockMvc.perform(get("/escalated/api/agent/tickets")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        void usingATokenRecordsWhenItWasLastUsed() throws Exception {
            AgentProfile agent = agent(false, true);
            String token = tokenFor(agent);

            mockMvc.perform(get("/escalated/api/agent/tickets")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            ApiToken stored = tokens.findByAgentIdOrderByCreatedAtDesc(agent.getId()).get(0);
            assertThat(stored.getLastUsedAt()).isNotNull();
        }

        @Test
        void anAgentTokenCannotReachTheAdminApi() throws Exception {
            AgentProfile agent = agent(false, true);
            String token = tokenFor(agent);

            mockMvc.perform(get("/escalated/api/admin/users")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anAdminTokenReachesTheAdminApi() throws Exception {
            AgentProfile admin = agent(true, true);
            String token = tokenFor(admin);

            mockMvc.perform(get("/escalated/api/admin/users")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        void anUnknownTokenIsUnauthorized() throws Exception {
            mockMvc.perform(get("/escalated/api/agent/tickets")
                            .header("Authorization", "Bearer not-a-real-token"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class HostRoutesAreLeftAlone {

        @Test
        void aHostRouteWithItsOwnBearerTokenIsNotTouched() throws Exception {
            // The host's own API authenticates its own tokens. Escalated's
            // token filter has no business rejecting them, and outside
            // /escalated/** it should not run at all.
            mockMvc.perform(get("/host/ping").header("Authorization", "Bearer xyz"))
                    .andExpect(status().isOk());
        }

        @Test
        void theJsonAuthRefreshEndpointAcceptsAHostIssuedToken() throws Exception {
            // /escalated/api/v1/auth/* exists to hand host-issued tokens to the
            // host's EscalatedApiAuthenticator. Those are not Escalated API
            // tokens, so the token filter must not answer for them.
            mockMvc.perform(post("/escalated/api/v1/auth/refresh")
                            .header("Authorization", "Bearer " + HostRoutes.HOST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("refreshed"));
        }

        @Test
        void theJsonAuthMeEndpointAcceptsAHostIssuedToken() throws Exception {
            mockMvc.perform(get("/escalated/api/v1/auth/me")
                            .header("Authorization", "Bearer " + HostRoutes.HOST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("flutter@host.test"));
        }
    }

    private AgentProfile agent(boolean admin, boolean isAgent) {
        AgentProfile profile = new AgentProfile();
        String unique = UUID.randomUUID().toString();
        profile.setName("Wiring " + unique);
        profile.setEmail("wiring-" + unique + "@example.com");
        profile.setAdmin(admin);
        profile.setAgent(isAgent);
        AgentProfile saved = agents.save(profile);
        createdAgents.add(saved.getId());
        return saved;
    }

    private String tokenFor(AgentProfile agent) {
        Map<String, Object> created = tokenService.createToken("wiring", agent.getId(), null, null);
        return (String) created.get("plainTextToken");
    }
}
