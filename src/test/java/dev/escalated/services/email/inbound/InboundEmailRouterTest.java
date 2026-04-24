package dev.escalated.services.email.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.email.MessageIdUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboundEmailRouterTest {

    private static final String DOMAIN = "support.example.com";
    private static final String SECRET = "test-secret-for-hmac";

    @Mock private TicketRepository ticketRepository;

    private EscalatedProperties properties;
    private InboundEmailRouter router;

    @BeforeEach
    void setUp() {
        properties = new EscalatedProperties();
        properties.getEmail().setDomain(DOMAIN);
        router = new InboundEmailRouter(ticketRepository, properties);
    }

    private Ticket mockTicket(long id, String reference) {
        Ticket t = new Ticket();
        t.setId(id);
        t.setTicketNumber(reference);
        return t;
    }

    private InboundMessage message(String inReplyTo, String references, String toEmail, String subject) {
        return new InboundMessage(
                "customer@example.com",
                "Customer",
                toEmail,
                subject,
                "body",
                null,
                null,
                inReplyTo,
                references,
                Map.of(),
                List.of()
        );
    }

    @Test
    void resolveTicket_matchesCanonicalInReplyTo() {
        Ticket ticket = mockTicket(42, "ESC-00042");
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));

        InboundMessage m = message(
                "<ticket-42@support.example.com>", null, "support@example.com", "re: hi");

        assertThat(router.resolveTicket(m)).contains(ticket);
    }

    @Test
    void resolveTicket_matchesReferencesHeaderCanonicalMessageId() {
        Ticket ticket = mockTicket(42, "ESC-00042");
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));

        InboundMessage m = message(
                null,
                "<unrelated@mail.com> <ticket-42@support.example.com>",
                "support@example.com",
                "re: hi");

        assertThat(router.resolveTicket(m)).contains(ticket);
    }

    @Test
    void resolveTicket_verifiesSignedReplyToWhenSecretConfigured() {
        properties.getEmail().setInboundSecret(SECRET);
        Ticket ticket = mockTicket(42, "ESC-00042");
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));

        String to = MessageIdUtil.buildReplyTo(42, SECRET, DOMAIN);
        InboundMessage m = message(null, null, to, "my issue");

        assertThat(router.resolveTicket(m)).contains(ticket);
    }

    @Test
    void resolveTicket_rejectsForgedReplyToSignature() {
        properties.getEmail().setInboundSecret("real-secret");

        String forged = MessageIdUtil.buildReplyTo(42, "wrong-secret", DOMAIN);
        InboundMessage m = message(null, null, forged, "try to take over");

        assertThat(router.resolveTicket(m)).isEmpty();
    }

    @Test
    void resolveTicket_ignoresSignedReplyToWhenSecretBlank() {
        // Even a valid address signed with SOME secret must be
        // ignored when the host hasn't configured one.
        String to = MessageIdUtil.buildReplyTo(42, SECRET, DOMAIN);
        InboundMessage m = message(null, null, to, "hi");

        assertThat(router.resolveTicket(m)).isEmpty();
    }

    @Test
    void resolveTicket_matchesSubjectReferenceTag() {
        Ticket ticket = mockTicket(99, "ESC-00099");
        when(ticketRepository.findByTicketNumber("ESC-00099")).thenReturn(Optional.of(ticket));

        InboundMessage m = message(null, null, "support@example.com", "RE: [ESC-00099] help");

        assertThat(router.resolveTicket(m)).contains(ticket);
    }

    @Test
    void resolveTicket_returnsEmptyWhenNothingMatches() {
        InboundMessage m = message(null, null, "support@example.com", "New issue");

        assertThat(router.resolveTicket(m)).isEmpty();
    }

    @Test
    void resolveTicket_nullMessageReturnsEmpty() {
        assertThat(router.resolveTicket(null)).isEmpty();
    }

    @Test
    void candidateHeaderMessageIds_inReplyToFirstThenReferences() {
        InboundMessage m = message(
                "<primary@mail>",
                "<a@mail> <b@mail>",
                "support@example.com",
                "re");

        List<String> ids = InboundEmailRouter.candidateHeaderMessageIds(m);

        assertThat(ids).containsExactly("<primary@mail>", "<a@mail>", "<b@mail>");
    }

    @Test
    void candidateHeaderMessageIds_emptyHeadersYieldsNone() {
        InboundMessage m = message(null, null, "support@example.com", "hi");

        assertThat(InboundEmailRouter.candidateHeaderMessageIds(m)).isEmpty();
    }
}
