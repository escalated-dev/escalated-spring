package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.Webhook;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.WebhookRepository;
import dev.escalated.services.ApiTokenService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Staff JSON as a real client receives it: a booted host, real HTTP, a real
 * API token. The staff routes still serialise entities, so every credential
 * an entity holds must be kept out of the serialiser itself.
 *
 * <p>An ordinary agent listing tickets got the assigned admin's
 * {@code twoFactorSecret} (the TOTP seed, enough to mint their codes) and the
 * requester's guest access token.
 */
@SpringBootTest(classes = HostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class StaffJsonSecretsTest {

    @LocalServerPort private int port;
    @Autowired private TicketService ticketService;
    @Autowired private AgentProfileRepository agents;
    @Autowired private ApiTokenService tokenService;
    @Autowired private WebhookRepository webhooks;

    private final HttpClient http = HttpClient.newHttpClient();

    private String totpSeed;
    private String webhookSecret;
    private Ticket ticket;
    private String agentToken;
    private String adminToken;

    @BeforeEach
    void seed() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        totpSeed = "TOTPSEED" + unique.toUpperCase();
        webhookSecret = "whsec-" + UUID.randomUUID();

        AgentProfile admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAdmin(true);
        admin.setAgent(true);
        admin.setTwoFactorSecret(totpSeed);
        admin = agents.save(admin);

        AgentProfile agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent.setTwoFactorSecret(totpSeed + "-AGENT");
        agent = agents.save(agent);

        ticket = ticketService.create("Secrets", "Body", "Guest", "guest-" + unique + "@example.com",
                TicketPriority.MEDIUM, null);
        ticketService.assign(ticket.getId(), admin.getId(), admin.getEmail());
        ticket = ticketService.findById(ticket.getId());

        Webhook webhook = new Webhook();
        webhook.setUrl("https://hooks.example.com/" + unique);
        webhook.setSecret(webhookSecret);
        webhook.setEvents("ticket.created");
        webhooks.save(webhook);

        agentToken = (String) tokenService.createToken("secrets", agent.getId(), null, null).get("plainTextToken");
        adminToken = (String) tokenService.createToken("secrets", admin.getId(), null, null).get("plainTextToken");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/escalated/api/agent/tickets",
        "/escalated/api/agent/tickets/{id}",
        "/escalated/api/agent/tickets/{id}/replies",
    })
    void anAgentNeverReceivesAnotherAgentsSecrets(String path) throws Exception {
        assertNoSecrets(get(path, agentToken));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/escalated/api/admin/tickets",
        "/escalated/api/admin/tickets/{id}",
        "/escalated/api/admin/agents",
        "/escalated/api/admin/webhooks",
    })
    void evenAnAdminNeverReceivesStoredCredentials(String path) throws Exception {
        assertNoSecrets(get(path, adminToken));
    }

    private void assertNoSecrets(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .doesNotContain(totpSeed)
                .doesNotContainIgnoringCase("twoFactorSecret")
                .doesNotContainIgnoringCase("two_factor_secret")
                .doesNotContain("guestAccessToken")
                .doesNotContain(webhookSecret)
                .doesNotContain("tokenHash")
                .doesNotContain("accessToken")
                .doesNotContain("hibernateLazyInitializer");
        assertThat(ticket.getGuestAccessToken()).isNotBlank();
        assertThat(response.body()).doesNotContain(ticket.getGuestAccessToken());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + path.replace("{id}", ticket.getId().toString())))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
