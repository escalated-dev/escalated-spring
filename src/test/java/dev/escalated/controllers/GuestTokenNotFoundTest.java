package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * A guest token that matches no ticket is a refused capability, not a server
 * error. {@code TicketService.findByGuestToken} throws
 * {@code EntityNotFoundException}, which nothing mapped to a status. The
 * container forwarded it to {@code /error}, which the host's own security
 * refuses for an anonymous caller, so a guest saw 401 with no body (and a
 * host without that security would have shown 500).
 *
 * <p>Requests go to a real server (no service stubs, no MockMvc), so the status
 * is the one a guest's browser would see. The status matches the NestJS
 * reference's {@code GuestAccessGuard}: 403 "Invalid guest access token".
 */
@SpringBootTest(classes = HostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class GuestTokenNotFoundTest {

    private static final String INVALID_TOKEN_BODY = "{\"error\":\"Invalid guest access token\"}";

    @LocalServerPort private int port;
    @Autowired private TicketService ticketService;

    private final HttpClient http = HttpClient.newHttpClient();

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,  /escalated/api/widget/tickets/{token}",
        "GET,  /escalated/api/widget/tickets/{token}/replies",
        "POST, /escalated/api/widget/tickets/{token}/replies",
        "GET,  /escalated/api/guest/tickets/{token}",
        "GET,  /escalated/api/guest/tickets/{token}/replies",
        "POST, /escalated/api/guest/tickets/{token}/replies",
    })
    void anUnknownGuestTokenIsRefusedWith403(String method, String path) throws Exception {
        HttpResponse<String> response = send(method, path.replace("{token}", "no-such-" + UUID.randomUUID()));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).isEqualTo(INVALID_TOKEN_BODY);
    }

    @Test
    void aKnownGuestTokenStillReachesItsTicket() throws Exception {
        Ticket ticket = ticketService.create("Help", "Details", "Guest",
                "guest-" + UUID.randomUUID() + "@example.com", TicketPriority.MEDIUM, null);

        HttpResponse<String> response = send("GET", "/escalated/api/guest/tickets/" + ticket.getGuestAccessToken());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if ("POST".equals(method)) {
            request.POST(HttpRequest.BodyPublishers.ofString("{\"body\":\"hi\"}"));
        } else {
            request.GET();
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
