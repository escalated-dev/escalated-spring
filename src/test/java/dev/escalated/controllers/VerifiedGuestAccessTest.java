package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
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
import dev.escalated.models.TicketPriority;
import dev.escalated.services.GuestCodeMailer;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Verified guest access, as escalated-laravel {@code docs/guest-access.md}
 * describes it and the shared widget uses it: emailed codes, expiring grants,
 * {@code /verification} and {@code /lookup}, and the widget config flags. All
 * through the real routes.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class VerifiedGuestAccessTest {

    private static final String WIDGET = "/escalated/api/widget";
    private static final String GUEST = "/escalated/api/guest";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TicketService tickets;
    @Autowired private EscalatedProperties properties;

    @MockitoBean private GuestCodeMailer mailer;

    private String email;

    @BeforeEach
    void seed() {
        email = "guest-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @AfterEach
    void restore() {
        properties.getGuestAccess().setLegacyTokens(true);
        properties.getGuestAccess().setCodesPerClientPerHour(10000);
        properties.getGuestAccess().setCodesPerMailboxPerHour(10000);
    }

    @Test
    void theWidgetConfigAdvertisesVerificationAndStatusLookup() throws Exception {
        mockMvc.perform(get(WIDGET + "/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest_verification_required").value(true))
                .andExpect(jsonPath("$.status_lookup_enabled").value(true));
    }

    @Test
    void aCodeIsEmailedAndOnlyItsIdIsAnswered() throws Exception {
        JsonNode answer = JSON.readTree(mockMvc.perform(guest(post(WIDGET + "/verification"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"purpose\":\"ticket\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.expires_in").value(600))
                .andReturn().getResponse().getContentAsString());

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq(email), code.capture(), eq("ticket"), eq(10));
        assertThat(code.getValue()).matches("[0-9]{8}");
        assertThat(answer.toString()).doesNotContain(code.getValue());

        String id = answer.get("verification_id").asText();
        var row = jdbc.queryForMap("SELECT * FROM escalated_guest_verifications WHERE public_id = ?", id);
        assertThat(row.toString()).doesNotContain(email).doesNotContain(code.getValue());
    }

    @Test
    void badRequestsForACodeAnswer422() throws Exception {
        mockMvc.perform(guest(post(GUEST + "/verification"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-address\",\"purpose\":\"everything\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("email")))
                .andExpect(jsonPath("$.errors", hasKey("purpose")));
    }

    @Test
    void codesPerMailboxAreBudgeted() throws Exception {
        properties.getGuestAccess().setCodesPerClientPerHour(3);
        String ip = nextIp();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(WIDGET + "/verification").with(remote(ip))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"purpose\":\"ticket\"}"))
                    .andExpect(status().isAccepted());
        }
        mockMvc.perform(post(WIDGET + "/verification").with(remote(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"purpose\":\"ticket\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // Another mailbox is unaffected.
        mockMvc.perform(post(WIDGET + "/verification").with(remote(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other-" + email + "\",\"purpose\":\"ticket\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void aWidgetTicketNeedsAProofAndAnswersAnExpiringGrant() throws Exception {
        mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(null, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("verification_id")));

        String[] proof = proof("ticket");
        JsonNode created = JSON.readTree(mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(proof[0], proof[1])))
                .andExpect(status().isCreated())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.guest_access_token", startsWith("eg1.")))
                .andExpect(jsonPath("$.expires_at").isString())
                .andReturn().getResponse().getContentAsString());
        String reference = created.get("reference").asText();
        String grant = created.get("guest_access_token").asText();

        mockMvc.perform(guest(get(WIDGET + "/tickets/{ref}", reference)).header("Authorization", "Bearer " + grant))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.status_label").value("Open"))
                .andExpect(jsonPath("$.status_color").isString())
                .andExpect(jsonPath("$.replies").isArray());

        mockMvc.perform(guest(post(WIDGET + "/tickets/{ref}/replies", reference))
                        .header("Authorization", "Bearer " + grant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Any news?\"}"))
                .andExpect(status().isCreated());

        // The same proof cannot be spent twice.
        mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(proof[0], proof[1])))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("verification_code")));
    }

    @Test
    void aProofIsBoundToItsEmailAndPurpose() throws Exception {
        String[] chatProof = proof("chat");
        mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(chatProof[0], chatProof[1])))
                .andExpect(status().isUnprocessableContent());

        String[] ticketProof = proof("ticket");
        String otherBody = ticketBody(ticketProof[0], ticketProof[1]).replace(email, "someone-else@example.com");
        mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otherBody))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void aCodeAllowsFiveGuessesAndExpires() throws Exception {
        String[] proof = proof("ticket");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(guest(post(WIDGET + "/tickets"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ticketBody(proof[0], "00000000".equals(proof[1]) ? "11111111" : "00000000")))
                    .andExpect(status().isUnprocessableContent());
        }
        mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(proof[0], proof[1])))
                .andExpect(status().isUnprocessableContent());

        String[] expired = proof("ticket");
        jdbc.update("UPDATE escalated_guest_verifications SET expires_at = ? WHERE public_id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), expired[0]);
        mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(expired[0], expired[1])))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void lookupIssuesAFreshGrantAndRevokesTheOldOne() throws Exception {
        JsonNode created = createTicket();
        String reference = created.get("reference").asText();
        String first = created.get("guest_access_token").asText();

        String[] proof = proof("lookup");
        JsonNode found = JSON.readTree(mockMvc.perform(guest(post(WIDGET + "/lookup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"reference\":\"" + reference + "\","
                                + "\"verification_id\":\"" + proof[0] + "\",\"verification_code\":\"" + proof[1] + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reference").value(reference))
                .andExpect(jsonPath("$.data[0].expires_at").isString())
                .andReturn().getResponse().getContentAsString());
        String second = found.get("data").get(0).get("guest_access_token").asText();

        mockMvc.perform(guest(get(WIDGET + "/tickets/{ref}", reference)).header("Authorization", "Bearer " + second))
                .andExpect(status().isOk());
        mockMvc.perform(guest(get(WIDGET + "/tickets/{ref}", reference)).header("Authorization", "Bearer " + first))
                .andExpect(status().isForbidden());
        // The guest prefix takes the same grant.
        mockMvc.perform(guest(get(GUEST + "/tickets/{ref}", reference)).header("Authorization", "Bearer " + second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference));
    }

    @Test
    void lookupWithTheWrongEmailFindsNothingAndSpendsTheProof() throws Exception {
        String reference = createTicket().get("reference").asText();
        String stranger = "stranger-" + email;
        String[] proof = proofFor(stranger, "lookup");
        String body = "{\"email\":\"" + stranger + "\",\"reference\":\"" + reference + "\","
                + "\"verification_id\":\"" + proof[0] + "\",\"verification_code\":\"" + proof[1] + "\"}";

        mockMvc.perform(guest(post(GUEST + "/lookup")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(guest(post(GUEST + "/lookup")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(guest(post(GUEST + "/lookup")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"reference\":\"" + reference + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("verification_id")));
    }

    @Test
    void aGrantOpensOnlyItsOwnTicketWhileCurrent() throws Exception {
        JsonNode mine = createTicket();
        email = "second-" + email;
        JsonNode other = createTicket();
        String grant = mine.get("guest_access_token").asText();
        String reference = mine.get("reference").asText();

        mockMvc.perform(guest(get(WIDGET + "/tickets/{ref}", other.get("reference").asText()))
                        .header("Authorization", "Bearer " + grant))
                .andExpect(status().isForbidden());
        mockMvc.perform(guest(get(WIDGET + "/tickets/{ref}", reference))
                        .header("Authorization", "Bearer " + grant.substring(0, grant.length() - 4) + "AAAA"))
                .andExpect(status().isForbidden());
        mockMvc.perform(guest(get(WIDGET + "/tickets/{ref}", reference)).header("Authorization", "Bearer eg1."))
                .andExpect(status().isForbidden());

        // A changed requester email revokes it.
        jdbc.update("UPDATE escalated_tickets SET requester_email = ? WHERE ticket_number = ?",
                "moved-" + email, reference);
        mockMvc.perform(guest(get(WIDGET + "/tickets/{ref}", reference)).header("Authorization", "Bearer " + grant))
                .andExpect(status().isForbidden());

        // So does expiry.
        String otherGrant = other.get("guest_access_token").asText();
        jdbc.update("UPDATE escalated_tickets SET guest_access_expires_at = ? WHERE ticket_number = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), other.get("reference").asText());
        mockMvc.perform(guest(get(WIDGET + "/tickets/{ref}", other.get("reference").asText()))
                        .header("Authorization", "Bearer " + otherGrant))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyTokensWorkUntilAGrantIsIssuedOrTheSettingIsOff() throws Exception {
        Ticket old = tickets.create("Before verification", "Body", "Old Guest", email, TicketPriority.LOW, null);
        String legacy = old.getGuestAccessToken();

        mockMvc.perform(guest(get(GUEST + "/tickets/{token}", legacy))).andExpect(status().isOk());
        mockMvc.perform(guest(get(WIDGET + "/tickets/{token}", legacy))).andExpect(status().isOk());

        properties.getGuestAccess().setLegacyTokens(false);
        mockMvc.perform(guest(get(GUEST + "/tickets/{token}", legacy))).andExpect(status().isForbidden());
        properties.getGuestAccess().setLegacyTokens(true);

        String[] proof = proof("lookup");
        mockMvc.perform(guest(post(WIDGET + "/lookup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"reference\":\"" + old.getTicketNumber() + "\","
                                + "\"verification_id\":\"" + proof[0] + "\",\"verification_code\":\"" + proof[1] + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reference").value(old.getTicketNumber()));
        mockMvc.perform(guest(get(GUEST + "/tickets/{token}", legacy))).andExpect(status().isForbidden());
    }

    @Test
    void aChatNeedsAChatProof() throws Exception {
        String body = "{\"name\":\"Visitor\",\"email\":\"" + email + "\",\"message\":\"Hello\"}";
        mockMvc.perform(guest(post(WIDGET + "/chat/start")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent());

        String[] proof = proof("chat");
        mockMvc.perform(guest(post(WIDGET + "/chat/start"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("}", ",\"verification_id\":\"" + proof[0]
                                + "\",\"verification_code\":\"" + proof[1] + "\"}")))
                .andExpect(status().isCreated());
    }

    private JsonNode createTicket() throws Exception {
        String[] proof = proof("ticket");
        return JSON.readTree(mockMvc.perform(guest(post(WIDGET + "/tickets"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(proof[0], proof[1])))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String ticketBody(String verificationId, String code) {
        return "{\"name\":\"Guest\",\"email\":\"" + email + "\",\"subject\":\"Where is my order?\","
                + "\"description\":\"It has not arrived.\""
                + (verificationId == null ? "" : ",\"verification_id\":\"" + verificationId + "\"")
                + (code == null ? "" : ",\"verification_code\":\"" + code + "\"")
                + "}";
    }

    /** Requests a code over HTTP and reads it from the email, as the guest would. */
    private String[] proof(String purpose) throws Exception {
        return proofFor(email, purpose);
    }

    private String[] proofFor(String address, String purpose) throws Exception {
        org.mockito.Mockito.clearInvocations(mailer);
        JsonNode answer = JSON.readTree(mockMvc.perform(guest(post(WIDGET + "/verification"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + address + "\",\"purpose\":\"" + purpose + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq(address), code.capture(), eq(purpose), anyInt());
        return new String[] {answer.get("verification_id").asText(), code.getValue()};
    }

    /** Each request from its own address, clear of the per-IP guest limits. */
    private static MockHttpServletRequestBuilder guest(MockHttpServletRequestBuilder request) {
        return request.with(remote(nextIp()));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor remote(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String nextIp() {
        int n = NEXT_IP.getAndIncrement();
        return "10.77." + (n / 250) + "." + (n % 250 + 1);
    }
}
