package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
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
 * A guest reply is written by the ticket's requester: the guest token is the
 * only credential, and it belongs to them. The reply endpoints took the
 * author's name and email from the request body, so a token holder could file
 * a reply under any address, and leaving the email out failed on the NOT NULL
 * author column. The NestJS reference files guest replies as
 * {@code ticket.requesterId} and reads nothing but {@code body}.
 *
 * <p>A booted host on a random port, real HTTP, nothing stubbed.
 */
@SpringBootTest(classes = HostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class GuestReplyAuthorTest {

    @LocalServerPort private int port;
    @Autowired private TicketService ticketService;
    @Autowired private ReplyRepository replies;

    private final HttpClient http = HttpClient.newHttpClient();

    private Ticket ticket;
    private String requesterEmail;

    @BeforeEach
    void seed() {
        requesterEmail = "requester-" + UUID.randomUUID() + "@example.com";
        ticket = ticketService.create("Parcel question", "Where is it?", "Rita Requester",
                requesterEmail, TicketPriority.MEDIUM, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/escalated/api/guest", "/escalated/api/widget"})
    void anEmailInTheBodyIsNotStoredAsTheAuthor(String prefix) throws Exception {
        int status = reply(prefix, "{\"body\":\"Spoofed\",\"name\":\"The CEO\",\"email\":\"ceo@victim.example\"}");

        assertThat(status).isBetween(200, 299);
        Reply stored = onlyReply();
        assertThat(stored.getBody()).isEqualTo("Spoofed");
        assertThat(stored.getAuthorEmail()).isEqualTo(requesterEmail);
        assertThat(stored.getAuthorName()).isEqualTo("Rita Requester");
        assertThat(stored.getAuthorType()).isEqualTo("customer");
        assertThat(stored.isInternal()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/escalated/api/guest", "/escalated/api/widget"})
    void aReplyWithNoNameOrEmailIsFiledAsTheRequester(String prefix) throws Exception {
        int status = reply(prefix, "{\"body\":\"Just the message\"}");

        assertThat(status).isBetween(200, 299);
        Reply stored = onlyReply();
        assertThat(stored.getAuthorEmail()).isEqualTo(requesterEmail);
        assertThat(stored.getAuthorName()).isEqualTo("Rita Requester");
    }

    private Reply onlyReply() {
        List<Reply> stored = replies.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        assertThat(stored).hasSize(1);
        return stored.get(0);
    }

    private int reply(String prefix, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + prefix + "/tickets/" + ticket.getGuestAccessToken() + "/replies"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}
