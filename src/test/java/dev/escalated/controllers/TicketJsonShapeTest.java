package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.ApiTokenService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Ticket JSON as a real client receives it: a booted host on a random port,
 * real HTTP, nothing stubbed.
 *
 * <p>The guest endpoints serialised the {@code Ticket} entity itself. Its
 * children point back at it ({@code Reply.ticket}, {@code TicketActivity.ticket},
 * ...), so the response nested until Jackson gave up: tens of kilobytes of
 * truncated, unparseable JSON under a 200. Along the way it handed a guest every
 * internal note, the activity log and the assigned agent. Guest endpoints now
 * answer with a guest-safe shape, and the staff endpoints that still serialise
 * the entity no longer loop.
 */
@SpringBootTest(classes = HostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class TicketJsonShapeTest {

    private static final int MAX_BYTES = 16_384;
    private static final int MAX_DEPTH = 12;
    private static final String INTERNAL_NOTE = "INTERNAL-NOTE-" + UUID.randomUUID();
    private static final String PUBLIC_REPLY = "Public answer";

    @LocalServerPort private int port;
    @Autowired private TicketService ticketService;
    @Autowired private AgentProfileRepository agents;
    @Autowired private ApiTokenService tokenService;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private Ticket ticket;
    private AgentProfile admin;

    @BeforeEach
    void seed() {
        admin = new AgentProfile();
        String unique = UUID.randomUUID().toString();
        admin.setName("Shape " + unique);
        admin.setEmail("shape-" + unique + "@example.com");
        admin.setAdmin(true);
        admin.setAgent(true);
        admin = agents.save(admin);

        ticket = ticketService.create("Parcel question", "Where is my parcel?", "Guest",
                "guest-" + unique + "@example.com", TicketPriority.MEDIUM, null);
        ticketService.assign(ticket.getId(), admin.getId(), admin.getEmail());
        ticketService.addTag(ticket.getId(), "shape-" + unique);
        ticketService.addReply(ticket.getId(), PUBLIC_REPLY, admin.getName(), admin.getEmail(), "agent", false);
        ticketService.addReply(ticket.getId(), INTERNAL_NOTE, admin.getName(), admin.getEmail(), "agent", true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/escalated/api/guest/tickets/{token}", "/escalated/api/widget/tickets/{token}"})
    void theGuestTicketViewIsBoundedAndGuestShaped(String path) throws Exception {
        JsonNode body = guestJson("GET", path);

        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "id", "reference", "subject", "description", "status", "priority", "channel",
                "department", "requester_name", "created_at", "updated_at", "resolved_at",
                "closed_at", "attachments", "replies");
        assertThat(body.get("reference").asText()).isEqualTo(ticket.getTicketNumber());
        assertThat(body.get("description").asText()).isEqualTo("Where is my parcel?");

        assertThat(body.get("replies")).hasSize(1);
        JsonNode reply = body.get("replies").get(0);
        assertThat(fieldNames(reply)).containsExactlyInAnyOrder(
                "id", "body", "author_name", "author_type", "is_agent", "created_at", "attachments");
        assertThat(reply.get("body").asText()).isEqualTo(PUBLIC_REPLY);
        assertThat(reply.get("is_agent").asBoolean()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/escalated/api/guest/tickets/{token}",
        "/escalated/api/widget/tickets/{token}",
        "/escalated/api/guest/tickets/{token}/replies",
        "/escalated/api/widget/tickets/{token}/replies",
    })
    void aGuestNeverSeesInternalNotesActivityOrStaffDetails(String path) throws Exception {
        String raw = guest("GET", path).body();

        assertThat(raw).doesNotContain(INTERNAL_NOTE);
        assertThat(raw).doesNotContain(admin.getEmail());
        assertThat(raw).doesNotContain("activities");
        assertThat(raw).doesNotContain("guestAccessToken");
        assertThat(depth(json.readTree(raw))).isLessThan(MAX_DEPTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/escalated/api/guest/tickets/{token}/replies", "/escalated/api/widget/tickets/{token}/replies"})
    void theGuestReplyListHoldsOnlyPublicReplies(String path) throws Exception {
        JsonNode body = guestJson("GET", path);

        assertThat(body.isArray()).isTrue();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("body").asText()).isEqualTo(PUBLIC_REPLY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/escalated/api/guest/tickets/{token}/replies", "/escalated/api/widget/tickets/{token}/replies"})
    void aGuestReplyIsAnsweredWithTheReplyAlone(String path) throws Exception {
        JsonNode body = guestJson("POST", path);

        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "id", "body", "author_name", "author_type", "is_agent", "created_at", "attachments");
        assertThat(body.get("body").asText()).isEqualTo("Thanks");
        assertThat(body.get("is_agent").asBoolean()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/escalated/api/admin/tickets/{id}",
        "/escalated/api/admin/tickets",
        "/escalated/api/agent/tickets/{id}",
        "/escalated/api/agent/tickets",
        "/escalated/api/agent/tickets/{id}/replies",
    })
    void staffTicketJsonIsBoundedAndParses(String path) throws Exception {
        Map<String, Object> created = tokenService.createToken("shape", admin.getId(), null, null);
        HttpResponse<String> response = send("GET", path.replace("{id}", ticket.getId().toString()),
                (String) created.get("plainTextToken"));

        assertThat(response.statusCode()).isEqualTo(200);
        // A list grows with the tickets in the database, so its size proves
        // nothing; a back-reference loop shows up as depth instead.
        assertThat(depth(json.readTree(response.body()))).isLessThan(MAX_DEPTH);
    }

    @Test
    void staffStillSeeTheTicketsRepliesAndActivity() throws Exception {
        Map<String, Object> created = tokenService.createToken("shape", admin.getId(), null, null);
        JsonNode body = json.readTree(send("GET", "/escalated/api/agent/tickets/" + ticket.getId(),
                (String) created.get("plainTextToken")).body());

        assertThat(body.get("replies")).hasSize(2);
        assertThat(body.get("activities").size()).isPositive();
        assertThat(body.toString()).contains(INTERNAL_NOTE);
    }

    private JsonNode guestJson(String method, String path) throws Exception {
        HttpResponse<String> response = guest(method, path);
        assertThat(response.statusCode()).isBetween(200, 201);
        assertThat(response.body().length()).isLessThan(MAX_BYTES);
        return json.readTree(response.body());
    }

    private HttpResponse<String> guest(String method, String path) throws Exception {
        return send(method, path.replace("{token}", ticket.getGuestAccessToken()), null);
    }

    private HttpResponse<String> send(String method, String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if ("POST".equals(method)) {
            request.POST(HttpRequest.BodyPublishers.ofString("{\"body\":\"Thanks\",\"name\":\"Guest\",\"email\":\"guest@example.com\"}"));
        } else {
            request.GET();
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int depth(JsonNode node) {
        int deepest = 0;
        for (JsonNode child : node) {
            deepest = Math.max(deepest, depth(child));
        }
        return node.isContainerNode() ? deepest + 1 : 0;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
