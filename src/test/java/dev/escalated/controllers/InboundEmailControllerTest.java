package dev.escalated.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.security.ApiTokenAuthenticationFilter;
import dev.escalated.services.email.inbound.InboundEmailParser;
import dev.escalated.services.email.inbound.InboundEmailService;
import dev.escalated.services.email.inbound.InboundEmailService.Outcome;
import dev.escalated.services.email.inbound.InboundEmailService.PendingAttachment;
import dev.escalated.services.email.inbound.InboundEmailService.ProcessResult;
import dev.escalated.services.email.inbound.InboundMessage;
import dev.escalated.services.email.inbound.PostmarkInboundParser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP-level tests for {@link InboundEmailController}.
 *
 * Mirrors the Go handler tests (escalated-go#34) and the .NET controller
 * tests (escalated-dotnet#28). Exercises signature verification, adapter
 * dispatch, and the full response shape produced by
 * {@link InboundEmailService#process(InboundMessage)}.
 *
 * The real {@link PostmarkInboundParser} is wired in via {@link Import}
 * so payload parsing isn't mocked — only the service boundary is.
 */
@WebMvcTest(InboundEmailController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PostmarkInboundParser.class)
class InboundEmailControllerTest {

    private static final String SECRET = "test-inbound-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiTokenAuthenticationFilter apiTokenFilter;

    @MockitoBean
    private InboundEmailService inboundService;

    @MockitoBean
    private EscalatedProperties properties;

    @BeforeEach
    void setUp() {
        EscalatedProperties.EmailProperties email = new EscalatedProperties.EmailProperties();
        email.setInboundSecret(SECRET);
        email.setDomain("support.example.com");
        when(properties.getEmail()).thenReturn(email);
    }

    @Test
    void newTicket_returnsCreatedNewOutcome() throws Exception {
        when(inboundService.process(any(InboundMessage.class)))
                .thenReturn(new ProcessResult(Outcome.CREATED_NEW, 101L, null, List.of()));

        mockMvc.perform(post("/escalated/webhook/email/inbound")
                        .param("adapter", "postmark")
                        .header("X-Escalated-Inbound-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "From": "alice@example.com",
                                    "FromName": "Alice",
                                    "To": "support@example.com",
                                    "Subject": "Help",
                                    "TextBody": "Broken widget"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.outcome").value("created_new"))
                .andExpect(jsonPath("$.ticketId").value(101))
                .andExpect(jsonPath("$.replyId").isEmpty())
                .andExpect(jsonPath("$.pendingAttachmentDownloads").isArray());
    }

    @Test
    void matchedReply_returnsRepliedToExistingOutcome() throws Exception {
        when(inboundService.process(any(InboundMessage.class)))
                .thenReturn(new ProcessResult(Outcome.REPLIED_TO_EXISTING, 55L, 202L, List.of()));

        mockMvc.perform(post("/escalated/webhook/email/inbound")
                        .param("adapter", "postmark")
                        .header("X-Escalated-Inbound-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "From": "alice@example.com",
                                    "To": "support@example.com",
                                    "Subject": "Re: Help",
                                    "TextBody": "More info",
                                    "Headers": [
                                        { "Name": "In-Reply-To", "Value": "<ticket-55@support.example.com>" }
                                    ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("replied_to_existing"))
                .andExpect(jsonPath("$.ticketId").value(55))
                .andExpect(jsonPath("$.replyId").value(202));
    }

    @Test
    void skipped_returnsSkippedOutcome() throws Exception {
        when(inboundService.process(any(InboundMessage.class)))
                .thenReturn(new ProcessResult(Outcome.SKIPPED, null, null, List.of()));

        mockMvc.perform(post("/escalated/webhook/email/inbound")
                        .param("adapter", "postmark")
                        .header("X-Escalated-Inbound-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "From": "no-reply@sns.amazonaws.com",
                                    "To": "support@example.com",
                                    "Subject": "SubscriptionConfirmation",
                                    "TextBody": ""
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("skipped"))
                .andExpect(jsonPath("$.ticketId").isEmpty());
    }

    @Test
    void surfacesProviderHostedAttachments() throws Exception {
        when(inboundService.process(any(InboundMessage.class)))
                .thenReturn(new ProcessResult(Outcome.CREATED_NEW, 101L, null, List.of(
                        new PendingAttachment(
                                "large.pdf", "application/pdf", 10_000_000L,
                                "https://mailgun.example/att/large"))));

        mockMvc.perform(post("/escalated/webhook/email/inbound")
                        .param("adapter", "postmark")
                        .header("X-Escalated-Inbound-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "From": "alice@example.com",
                                    "To": "support@example.com",
                                    "Subject": "With attachments",
                                    "TextBody": "See attached"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pendingAttachmentDownloads[0].name").value("large.pdf"))
                .andExpect(jsonPath("$.pendingAttachmentDownloads[0].downloadUrl")
                        .value("https://mailgun.example/att/large"));
    }

    @Test
    void missingSecret_returns401() throws Exception {
        mockMvc.perform(post("/escalated/webhook/email/inbound")
                        .param("adapter", "postmark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inboundService);
    }

    @Test
    void badSecret_returns401() throws Exception {
        mockMvc.perform(post("/escalated/webhook/email/inbound")
                        .param("adapter", "postmark")
                        .header("X-Escalated-Inbound-Secret", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inboundService);
    }

    @Test
    void unknownAdapter_returns400() throws Exception {
        mockMvc.perform(post("/escalated/webhook/email/inbound")
                        .param("adapter", "nonesuch")
                        .header("X-Escalated-Inbound-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(inboundService);
    }

    @Test
    void missingAdapter_returns400() throws Exception {
        mockMvc.perform(post("/escalated/webhook/email/inbound")
                        .header("X-Escalated-Inbound-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(inboundService);
    }

    @Test
    void adapterHeader_isAcceptedAsFallbackToQueryParam() throws Exception {
        when(inboundService.process(any(InboundMessage.class)))
                .thenReturn(new ProcessResult(Outcome.SKIPPED, null, null, List.of()));

        mockMvc.perform(post("/escalated/webhook/email/inbound")
                        .header("X-Escalated-Adapter", "postmark")
                        .header("X-Escalated-Inbound-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "From": "no-reply@sns.amazonaws.com",
                                    "To": "support@example.com",
                                    "Subject": "SubscriptionConfirmation",
                                    "TextBody": ""
                                }
                                """))
                .andExpect(status().isAccepted());
        verify(inboundService).process(any(InboundMessage.class));
    }
}
