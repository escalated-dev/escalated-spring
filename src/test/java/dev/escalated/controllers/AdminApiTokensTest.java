package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import hostapp.HostApplication;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /escalated/api/admin/tokens}, which the README documents, and
 * token abilities ({@code agent}, {@code admin}, {@code *}) enforced on the
 * agent and admin APIs, as in Laravel's {@code AuthenticateApiToken}.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class AdminApiTokensTest {

    private static final String TOKENS = "/escalated/api/admin/tokens";
    private static final String AGENT_ROUTE = "/escalated/api/agent/tickets";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private JdbcTemplate jdbc;

    private AgentProfile admin;

    @BeforeEach
    void seed() {
        admin = staff(true);
    }

    @Test
    void theReadmesRequestIssuesATokenThatWorksOnce() throws Exception {
        JsonNode created = issue("{\"name\": \"My API Token\", \"agent_id\": " + admin.getId() + "}");

        String plain = created.get("plain_text_token").asText();
        assertThat(plain).isNotBlank();
        assertThat(created.get("token").get("abilities").get(0).asText()).isEqualTo("*");
        assertThat(created.get("token").has("token_hash")).isFalse();
        assertThat(created.get("token").has("tokenHash")).isFalse();

        mockMvc.perform(get(AGENT_ROUTE).header("Authorization", "Bearer " + plain)).andExpect(status().isOk());
        mockMvc.perform(get(TOKENS).header("Authorization", "Bearer " + plain)).andExpect(status().isOk());

        String list = mockMvc.perform(get(TOKENS).with(user(admin.getEmail())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).contains("My API Token").doesNotContain(plain);
    }

    @Test
    void anAgentOnlyTokenCannotReachTheAdminApi() throws Exception {
        String plain = issue("{\"name\":\"agent only\",\"agent_id\":" + admin.getId() + ",\"abilities\":[\"agent\"]}")
                .get("plain_text_token").asText();

        mockMvc.perform(get(AGENT_ROUTE).header("Authorization", "Bearer " + plain)).andExpect(status().isOk());
        mockMvc.perform(get(TOKENS).header("Authorization", "Bearer " + plain)).andExpect(status().isForbidden());
    }

    @Test
    void anAdminOnlyTokenNeedsTheAgentAbilityForTheAgentApi() throws Exception {
        String plain = issue("{\"name\":\"admin only\",\"agent_id\":" + admin.getId() + ",\"abilities\":[\"admin\"]}")
                .get("plain_text_token").asText();

        mockMvc.perform(get(TOKENS).header("Authorization", "Bearer " + plain)).andExpect(status().isOk());
        mockMvc.perform(get(AGENT_ROUTE).header("Authorization", "Bearer " + plain)).andExpect(status().isForbidden());
    }

    @Test
    void abilitiesNeverExceedTheOwnersGates() throws Exception {
        AgentProfile agent = staff(false);
        String plain = issue("{\"name\":\"wide\",\"agent_id\":" + agent.getId() + ",\"abilities\":[\"*\"]}")
                .get("plain_text_token").asText();

        mockMvc.perform(get(AGENT_ROUTE).header("Authorization", "Bearer " + plain)).andExpect(status().isOk());
        mockMvc.perform(get(TOKENS).header("Authorization", "Bearer " + plain)).andExpect(status().isForbidden());
    }

    @Test
    void invalidRequestsAnswer422() throws Exception {
        mockMvc.perform(post(TOKENS).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agent_id\":987654321,\"abilities\":[\"root\"],\"expires_in_days\":0}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("name")))
                .andExpect(jsonPath("$.errors", hasKey("agent_id")))
                .andExpect(jsonPath("$.errors", hasKey("abilities.0")))
                .andExpect(jsonPath("$.errors", hasKey("expires_in_days")));
    }

    @Test
    void expiryUpdatesAndRevocation() throws Exception {
        JsonNode created = issue("{\"name\":\"short\",\"agent_id\":" + admin.getId() + ",\"expires_in_days\":1}");
        long id = created.get("token").get("id").asLong();
        String plain = created.get("plain_text_token").asText();
        assertThat(created.get("token").get("expires_at").isNull()).isFalse();

        mockMvc.perform(put(TOKENS + "/{id}", id).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"renamed\",\"abilities\":[\"agent\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed"))
                .andExpect(jsonPath("$.abilities[0]").value("agent"));
        // Narrowed abilities apply on the next request.
        mockMvc.perform(get(TOKENS).header("Authorization", "Bearer " + plain)).andExpect(status().isForbidden());

        jdbc.update("UPDATE escalated_api_tokens SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), id);
        mockMvc.perform(get(AGENT_ROUTE).header("Authorization", "Bearer " + plain))
                .andExpect(status().isUnauthorized());

        JsonNode other = issue("{\"name\":\"revoked\",\"agent_id\":" + admin.getId() + "}");
        mockMvc.perform(delete(TOKENS + "/{id}", other.get("token").get("id").asLong()).with(user(admin.getEmail())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(AGENT_ROUTE).header("Authorization", "Bearer " + other.get("plain_text_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminsIssueTokens() throws Exception {
        AgentProfile agent = staff(false);
        mockMvc.perform(post(TOKENS).with(user(agent.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"mine\",\"agent_id\":" + agent.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    private JsonNode issue(String body) throws Exception {
        return JSON.readTree(mockMvc.perform(post(TOKENS).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private AgentProfile staff(boolean isAdmin) {
        AgentProfile profile = new AgentProfile();
        profile.setName("Staff");
        profile.setEmail("staff-" + UUID.randomUUID() + "@example.com");
        profile.setAgent(true);
        profile.setAdmin(isAdmin);
        return agents.save(profile);
    }
}
