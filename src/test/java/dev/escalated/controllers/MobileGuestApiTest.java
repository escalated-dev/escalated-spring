package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.GuestCodeMailer;
import dev.escalated.services.SettingsService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The mobile guest API (escalated-developer-context
 * {@code domain-model/mobile-guest-api.md}), through the real routes: the
 * same codes, grants and guest budgets as the widget and guest routes.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class MobileGuestApiTest {

    private static final String MOBILE = "/escalated/api/v1/mobile/guest";
    private static final String WIDGET = "/escalated/api/widget";
    private static final String GUEST = "/escalated/api/guest";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TicketService tickets;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ContactRepository contacts;
    @Autowired private SettingsService settings;
    @Autowired private EscalatedProperties properties;

    @MockitoBean private GuestCodeMailer mailer;

    private String email;

    @BeforeEach
    void seed() {
        email = "mobile-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @AfterEach
    void restore() {
        settings.delete("guest_tickets_enabled");
        EscalatedProperties.GuestRateLimitProperties limits = properties.getGuestRateLimit();
        limits.setTicketsPerMinute(5);
        limits.setRepliesPerMinute(10);
        limits.setRequestsPerMinute(60);
    }

    @Test
    void createWithoutProofIs422AndCreatesNothing() throws Exception {
        perform(post(MOBILE + "/tickets"), ticketBody(email, null, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("verification_id")))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        // A wrong code is refused on verification_code, and still creates nothing.
        String[] proof = proof("ticket");
        perform(post(MOBILE + "/tickets"), ticketBody(email, proof[0], "00000000".equals(proof[1]) ? "11111111" : "00000000"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("verification_code")));

        assertThat(ticketRepository.countByRequesterEmail(email)).isZero();
        assertThat(contacts.findByEmail(email)).isEmpty();
    }

    @Test
    void fieldsAreValidatedBeforeTheCodeIsSpent() throws Exception {
        String[] proof = proof("ticket");
        perform(post(MOBILE + "/tickets"), "{\"email\":\"" + email + "\",\"verification_id\":\"" + proof[0]
                + "\",\"verification_code\":\"" + proof[1] + "\",\"priority\":\"whenever\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("name")))
                .andExpect(jsonPath("$.errors", hasKey("subject")))
                .andExpect(jsonPath("$.errors", hasKey("description")))
                .andExpect(jsonPath("$.errors", hasKey("priority")))
                .andExpect(jsonPath("$.errors", not(hasKey("verification_code"))));

        // The code was not spent by the invalid request.
        perform(post(MOBILE + "/tickets"), ticketBody(email, proof[0], proof[1]))
                .andExpect(status().isCreated());
    }

    @Test
    void verificationThenCreateAnswersAMobileTicketWithAGrant() throws Exception {
        String mixedCase = email.toUpperCase(Locale.ROOT);
        String[] proof = proofFor(mixedCase, "ticket");
        String body = ticketBody(mixedCase, proof[0], proof[1]).replace("}",
                ",\"priority\":\"high\",\"status\":\"closed\",\"metadata\":{\"vip\":true},"
                        + "\"external_reference\":\"X-1\",\"unknown\":1}");

        JsonNode created = JSON.readTree(perform(post(MOBILE + "/tickets"), body)
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.message").value("Ticket created."))
                .andExpect(jsonPath("$.data.guest_access_token", startsWith("eg1.")))
                .andExpect(jsonPath("$.data.guest_access_expires_at").isString())
                .andExpect(jsonPath("$.data.subject").value("Where is my order?"))
                .andExpect(jsonPath("$.data.description").value("It has not arrived."))
                .andExpect(jsonPath("$.data.status.value").value("open"))
                .andExpect(jsonPath("$.data.status.label").value("Open"))
                .andExpect(jsonPath("$.data.priority.value").value("high"))
                .andExpect(jsonPath("$.data.priority.label").value("High"))
                .andExpect(jsonPath("$.data.channel").value("web"))
                .andExpect(jsonPath("$.data.metadata").isEmpty())
                .andExpect(jsonPath("$.data.requester.name").value("Mobile Guest"))
                .andExpect(jsonPath("$.data.requester.email").value(email))
                .andExpect(jsonPath("$.data.assignee").doesNotExist())
                .andExpect(jsonPath("$.data.tags").isEmpty())
                .andExpect(jsonPath("$.data.replies").isEmpty())
                .andExpect(jsonPath("$.data.activities").isEmpty())
                .andExpect(jsonPath("$.data.sla", hasKey("first_response_breached")))
                .andExpect(jsonPath("$.data.is_following").value(false))
                .andExpect(jsonPath("$.data.followers_count").value(0))
                .andExpect(jsonPath("$.data", not(hasKey("external_reference"))))
                .andExpect(jsonPath("$.data.created_at").isString())
                .andReturn().getResponse().getContentAsString());

        Ticket ticket = ticketRepository.findById(created.get("data").get("id").asLong()).orElseThrow();
        assertThat(ticket.getRequesterEmail()).isEqualTo(email);
        assertThat(ticket.getChannel()).isEqualTo("web");
        assertThat(ticket.getStatus().name()).isEqualTo("OPEN");
        assertThat(ticket.getMetadata() == null || ticket.getMetadata().isEmpty()).isTrue();
        assertThat(contacts.findByEmail(email)).isPresent();

        // The code is spent.
        perform(post(MOBILE + "/tickets"), ticketBody(email, proof[0], proof[1]))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("verification_code")));
    }

    @Test
    void aMobileGrantOpensTheMobileWidgetAndGuestRoutes() throws Exception {
        JsonNode data = createMobileTicket();
        String grant = data.get("guest_access_token").asText();
        String reference = data.get("reference").asText();

        perform(get(MOBILE + "/tickets/{token}", grant))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.data.reference").value(reference))
                .andExpect(jsonPath("$.data.guest_access_token").value(grant))
                .andExpect(jsonPath("$.data.guest_access_expires_at").isString());
        perform(get(WIDGET + "/tickets/{ref}", reference).header("Authorization", "Bearer " + grant))
                .andExpect(status().isOk());
        perform(get(GUEST + "/tickets/{ref}", reference).header("Authorization", "Bearer " + grant))
                .andExpect(status().isOk());
    }

    @Test
    void widgetAndLookupGrantsOpenTheMobileRoutesAndLookupRotates() throws Exception {
        String[] ticketProof = proof("ticket");
        JsonNode widget = JSON.readTree(perform(post(WIDGET + "/tickets"), ticketBody(email, ticketProof[0], ticketProof[1]))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String first = widget.get("guest_access_token").asText();
        String reference = widget.get("reference").asText();
        perform(get(MOBILE + "/tickets/{token}", first)).andExpect(status().isOk());

        String[] proof = proof("lookup");
        JsonNode found = JSON.readTree(perform(post(MOBILE + "/lookup"), "{\"email\":\"" + email
                + "\",\"reference\":\"" + reference + "\",\"verification_id\":\"" + proof[0]
                + "\",\"verification_code\":\"" + proof[1] + "\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].reference").value(reference))
                .andExpect(jsonPath("$.data[0].expires_at").isString())
                .andReturn().getResponse().getContentAsString());
        String second = found.get("data").get(0).get("guest_access_token").asText();

        perform(get(MOBILE + "/tickets/{token}", second)).andExpect(status().isOk());
        perform(get(MOBILE + "/tickets/{token}", first)).andExpect(status().isNotFound());

        // The code is spent either way.
        perform(post(MOBILE + "/lookup"), "{\"email\":\"" + email + "\",\"reference\":\"" + reference
                + "\",\"verification_id\":\"" + proof[0] + "\",\"verification_code\":\"" + proof[1] + "\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("verification_code")));
    }

    @Test
    void repliesCheckTheGrantTheClosedStateTheBodyAndTheEmail() throws Exception {
        JsonNode data = createMobileTicket();
        String grant = data.get("guest_access_token").asText();
        long id = data.get("id").asLong();

        perform(post(MOBILE + "/tickets/{token}/replies", grant),
                "{\"email\":\"  " + email.toUpperCase(Locale.ROOT) + " \",\"body\":\"Thanks\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.message").value("Reply sent."));

        perform(post(MOBILE + "/tickets/{token}/replies", grant), "{\"email\":\"someone@example.com\",\"body\":\"Hi\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("The provided email does not match this ticket."));

        perform(post(MOBILE + "/tickets/{token}/replies", grant), "{\"email\":\"" + email + "\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("body")));

        perform(get(MOBILE + "/tickets/{token}", grant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replies", hasSize(1)))
                .andExpect(jsonPath("$.data.replies[0].body").value("Thanks"))
                .andExpect(jsonPath("$.data.replies[0].is_internal_note").value(false))
                .andExpect(jsonPath("$.data.replies[0].author.id").value(0))
                .andExpect(jsonPath("$.data.replies[0].author.name").value("Mobile Guest"))
                .andExpect(jsonPath("$.data.replies[0].author.email").value(email));

        jdbc.update("UPDATE escalated_tickets SET status = 'CLOSED' WHERE id = ?", id);
        perform(post(MOBILE + "/tickets/{token}/replies", grant), "{\"email\":\"" + email + "\",\"body\":\"Again\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("This ticket is closed."));
    }

    @Test
    void expiredMalformedOverLongAndChatGrantsAre404() throws Exception {
        JsonNode data = createMobileTicket();
        String grant = data.get("guest_access_token").asText();

        perform(get(MOBILE + "/tickets/{token}", "eg1.not-a-grant"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        perform(get(MOBILE + "/tickets/{token}", grant + "A".repeat(5000))).andExpect(status().isNotFound());
        // The ticket's permanent token is not a grant here.
        String legacy = jdbc.queryForObject("SELECT guest_access_token FROM escalated_tickets WHERE id = ?",
                String.class, data.get("id").asLong());
        if (legacy != null) {
            perform(get(MOBILE + "/tickets/{token}", legacy)).andExpect(status().isNotFound());
        }

        String[] chatProof = proof("chat");
        JsonNode chat = JSON.readTree(perform(post(WIDGET + "/chat/start"), "{\"name\":\"Visitor\",\"email\":\""
                + email + "\",\"message\":\"Hello\",\"verification_id\":\"" + chatProof[0]
                + "\",\"verification_code\":\"" + chatProof[1] + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String chatToken = chat.get("session_id").asText();
        perform(get(MOBILE + "/tickets/{token}", chatToken)).andExpect(status().isNotFound());
        perform(post(MOBILE + "/tickets/{token}/replies", chatToken), "{\"email\":\"" + email + "\",\"body\":\"Hi\"}")
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE escalated_tickets SET guest_access_expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), data.get("id").asLong());
        perform(get(MOBILE + "/tickets/{token}", grant)).andExpect(status().isNotFound());
        perform(post(MOBILE + "/tickets/{token}/replies", grant), "{\"email\":\"" + email + "\",\"body\":\"Hi\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void internalNotesAndStaffEmailsNeverAppear() throws Exception {
        JsonNode data = createMobileTicket();
        String grant = data.get("guest_access_token").asText();
        long id = data.get("id").asLong();
        tickets.addReply(id, "Secret note", "Agent Smith", "agent.smith@example.com", "agent", true);
        tickets.addReply(id, "We are on it", "Agent Smith", "agent.smith@example.com", "agent", false);

        String body = perform(get(MOBILE + "/tickets/{token}", grant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replies", hasSize(1)))
                .andExpect(jsonPath("$.data.replies[0].body").value("We are on it"))
                .andExpect(jsonPath("$.data.replies[0].author.id").value(0))
                .andExpect(jsonPath("$.data.replies[0].author.name").value("Agent Smith"))
                .andExpect(jsonPath("$.data.replies[0].author.email").value(""))
                .andExpect(jsonPath("$.data.replies[0].attachments").isArray())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Secret note").doesNotContain("agent.smith@example.com");
    }

    @Test
    void theGuestBudgetsAnswer429WithRetryAfterAndAreSharedWithTheWidget() throws Exception {
        // Submissions: the widget and the mobile create share one budget per IP.
        properties.getGuestRateLimit().setTicketsPerMinute(1);
        String ip = nextIp();
        mockMvc.perform(post(WIDGET + "/tickets").with(remote(ip)).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(email, null, null)))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post(MOBILE + "/tickets").with(remote(ip)).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(email, null, null)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        // Requests: every mobile guest route counts.
        properties.getGuestRateLimit().setRequestsPerMinute(2);
        String other = nextIp();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get(MOBILE + "/tickets/{token}", "eg1.nope").with(remote(other)))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get(MOBILE + "/tickets/{token}", "eg1.nope").with(remote(other)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void theRepliesBudgetCoversLookupAndReplies() throws Exception {
        properties.getGuestRateLimit().setRepliesPerMinute(1);
        String ip = nextIp();
        mockMvc.perform(post(MOBILE + "/lookup").with(remote(ip)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"reference\":\"ESC-1\"}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post(MOBILE + "/tickets/{token}/replies", "eg1.nope").with(remote(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"body\":\"Hi\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void guestTicketsOffRefusesCreateLookupAndTicketCodes() throws Exception {
        settings.set("guest_tickets_enabled", "0", "general");
        perform(post(MOBILE + "/tickets"), ticketBody(email, null, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Guest tickets are not enabled."))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        perform(post(MOBILE + "/verification"), "{\"email\":\"" + email + "\",\"purpose\":\"ticket\"}")
                .andExpect(status().isForbidden());
        perform(post(MOBILE + "/lookup"), "{\"email\":\"" + email + "\",\"reference\":\"ESC-1\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void verificationAnswersTheSharedChallenge() throws Exception {
        perform(post(MOBILE + "/verification"), "{\"email\":\"" + email + "\",\"purpose\":\"ticket\"}")
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.verification_id").isString())
                .andExpect(jsonPath("$.expires_in").value(600))
                .andExpect(jsonPath("$.message").isString());
        perform(post(MOBILE + "/verification"), "{\"email\":\"nope\",\"purpose\":\"other\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("email")))
                .andExpect(jsonPath("$.errors", hasKey("purpose")));
    }

    private JsonNode createMobileTicket() throws Exception {
        String[] proof = proof("ticket");
        return JSON.readTree(perform(post(MOBILE + "/tickets"), ticketBody(email, proof[0], proof[1]))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("data");
    }

    private static String ticketBody(String address, String verificationId, String code) {
        return "{\"name\":\"Mobile Guest\",\"email\":\"" + address + "\",\"subject\":\"Where is my order?\","
                + "\"description\":\"It has not arrived.\""
                + (verificationId == null ? "" : ",\"verification_id\":\"" + verificationId + "\"")
                + (code == null ? "" : ",\"verification_code\":\"" + code + "\"")
                + "}";
    }

    private String[] proof(String purpose) throws Exception {
        return proofFor(email, purpose);
    }

    /** Requests a code over the mobile route and reads it from the email, as the guest would. */
    private String[] proofFor(String address, String purpose) throws Exception {
        org.mockito.Mockito.clearInvocations(mailer);
        JsonNode answer = JSON.readTree(perform(post(MOBILE + "/verification"),
                        "{\"email\":\"" + address + "\",\"purpose\":\"" + purpose + "\"}")
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq(address.trim().toLowerCase(Locale.ROOT)), code.capture(), eq(purpose), anyInt());
        return new String[] {answer.get("verification_id").asText(), code.getValue()};
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(remote(nextIp())));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(remote(nextIp())).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static RequestPostProcessor remote(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String nextIp() {
        int n = NEXT_IP.getAndIncrement();
        return "10.88." + (n / 250) + "." + (n % 250 + 1);
    }
}
