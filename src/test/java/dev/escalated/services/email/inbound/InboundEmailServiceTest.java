package dev.escalated.services.email.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.TicketService;
import dev.escalated.services.email.MessageIdUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Who an inbound email may post as once it matches a ticket thread.
 * A thread match is only a lookup: the reply is accepted only from the
 * ticket's requester, and is posted as that requester.
 */
@ExtendWith(MockitoExtension.class)
class InboundEmailServiceTest {

    private static final String DOMAIN = "support.example.com";
    private static final String SECRET = "test-secret-for-hmac";

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketService ticketService;

    private EscalatedProperties properties;
    private InboundEmailService service;

    @BeforeEach
    void setUp() {
        properties = new EscalatedProperties();
        properties.getEmail().setDomain(DOMAIN);
        service = new InboundEmailService(new InboundEmailRouter(ticketRepository, properties), ticketService);

        Ticket created = new Ticket();
        created.setId(500L);
        lenient().when(ticketService.create(anyString(), anyString(), any(), anyString(), any(), any()))
                .thenReturn(created);
        Reply reply = new Reply();
        reply.setId(900L);
        lenient().when(ticketService.addReply(anyLong(), anyString(), any(), any(), anyString(), anyBoolean()))
                .thenReturn(reply);
    }

    private Ticket ticket(long id, String reference, String requesterEmail, TicketStatus status) {
        Ticket t = new Ticket();
        t.setId(id);
        t.setTicketNumber(reference);
        t.setRequesterName("Owner");
        t.setRequesterEmail(requesterEmail);
        t.setStatus(status);
        return t;
    }

    private InboundMessage message(String from, String to, String subject, String inReplyTo) {
        return new InboundMessage(from, "Sender", to, subject, "body text", null, null,
                inReplyTo, null, Map.of(), List.of());
    }

    @Test
    void strangerQuotingSubjectReference_opensNewTicket() {
        Ticket owned = ticket(7001L, "ESC-07001", "owner@example.com", TicketStatus.OPEN);
        when(ticketRepository.findByTicketNumber("ESC-07001")).thenReturn(Optional.of(owned));

        InboundEmailService.ProcessResult result = service.process(
                message("stranger@example.net", "support@example.com", "RE: [ESC-07001] Your order", null));

        assertThat(result.outcome()).isEqualTo(InboundEmailService.Outcome.CREATED_NEW);
        assertThat(result.ticketId()).isEqualTo(500L);
        verify(ticketService, never()).addReply(anyLong(), anyString(), any(), any(), anyString(), anyBoolean());
        verify(ticketService).create(eq("RE: [ESC-07001] Your order"), eq("body text"), eq("Sender"),
                eq("stranger@example.net"), eq(TicketPriority.MEDIUM), eq(null));
    }

    @Test
    void strangerThreadingOntoClosedTicket_doesNotReopenIt() {
        Ticket closed = ticket(42L, "ESC-00042", "owner@example.com", TicketStatus.CLOSED);
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(closed));

        InboundEmailService.ProcessResult result = service.process(message(
                "stranger@example.net", "support@example.com", "RE: [ESC-00042] Closed",
                "<ticket-42@support.example.com>"));

        assertThat(result.outcome()).isEqualTo(InboundEmailService.Outcome.CREATED_NEW);
        verify(ticketService, never()).changeStatus(anyLong(), any(), any());
        verify(ticketService, never()).addReply(anyLong(), anyString(), any(), any(), anyString(), anyBoolean());
    }

    @Test
    void spoofedAgentFrom_isNeverPostedAsTheAgent() {
        properties.getEmail().setInboundSecret(SECRET);
        Ticket owned = ticket(42L, "ESC-00042", "owner@example.com", TicketStatus.OPEN);
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(owned));

        InboundEmailService.ProcessResult result = service.process(message(
                "agent@example.com", MessageIdUtil.buildReplyTo(42L, SECRET, DOMAIN),
                "RE: [ESC-00042] Update", "<ticket-42@support.example.com>"));

        assertThat(result.outcome()).isEqualTo(InboundEmailService.Outcome.CREATED_NEW);
        verify(ticketService, never()).addReply(anyLong(), anyString(), any(), any(), anyString(), anyBoolean());
        verify(ticketService, never()).addReply(anyLong(), anyString(), any(), eq("agent@example.com"),
                anyString(), anyBoolean());
    }

    @Test
    void requesterReply_isAcceptedCaseInsensitively_postedAsRequester_andReopens() {
        properties.getEmail().setInboundSecret(SECRET);
        Ticket resolved = ticket(42L, "ESC-00042", "owner@example.com", TicketStatus.RESOLVED);
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(resolved));

        InboundEmailService.ProcessResult result = service.process(message(
                " Owner@Example.COM ", MessageIdUtil.buildReplyTo(42L, SECRET, DOMAIN),
                "RE: Question", "<ticket-42@support.example.com>"));

        assertThat(result.outcome()).isEqualTo(InboundEmailService.Outcome.REPLIED_TO_EXISTING);
        assertThat(result.ticketId()).isEqualTo(42L);
        assertThat(result.replyId()).isEqualTo(900L);
        verify(ticketService).addReply(42L, "body text", "Owner", "owner@example.com", "inbound_email", false);
        verify(ticketService).changeStatus(42L, TicketStatus.OPEN, "owner@example.com");
        verify(ticketService, never()).create(anyString(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void requesterReplyToOpenTicket_doesNotChangeStatus() {
        Ticket open = ticket(42L, "ESC-00042", "owner@example.com", TicketStatus.OPEN);
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(open));

        InboundEmailService.ProcessResult result = service.process(message(
                "owner@example.com", "support@example.com", "RE: hi", "<ticket-42@support.example.com>"));

        assertThat(result.outcome()).isEqualTo(InboundEmailService.Outcome.REPLIED_TO_EXISTING);
        verify(ticketService, never()).changeStatus(anyLong(), any(), any());
    }

    @Test
    void signedReplyToIsRequiredOnceSecretConfigured() {
        properties.getEmail().setInboundSecret(SECRET);
        Ticket owned = ticket(42L, "ESC-00042", "owner@example.com", TicketStatus.OPEN);
        lenient().when(ticketRepository.findById(42L)).thenReturn(Optional.of(owned));
        lenient().when(ticketRepository.findByTicketNumber("ESC-00042")).thenReturn(Optional.of(owned));

        InboundEmailService.ProcessResult result = service.process(message(
                "owner@example.com", "support@support.example.com", "RE: [ESC-00042] Question",
                "<ticket-42@support.example.com>"));

        assertThat(result.outcome()).isEqualTo(InboundEmailService.Outcome.CREATED_NEW);
        verify(ticketService, never()).addReply(anyLong(), anyString(), any(), any(), anyString(), anyBoolean());
    }
}
