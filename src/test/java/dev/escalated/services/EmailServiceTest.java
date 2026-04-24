package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;

/**
 * Unit tests for {@link EmailService}'s Message-ID / Reply-To wiring.
 *
 * <p>Uses a real {@link MimeMessage} + a {@link Mock} JavaMailSender so
 * we can assert on the headers that the service actually sets. The
 * Thymeleaf template engine is mocked to avoid pulling in real
 * templates for these unit tests.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private TemplateEngine templateEngine;

    private EmailService service;
    private EscalatedProperties properties;

    @BeforeEach
    void setUp() {
        properties = new EscalatedProperties();
        properties.getEmail().setDomain("support.example.com");
        properties.getEmail().setInboundSecret("test-secret-for-hmac");
        service = new EmailService(mailSender, templateEngine, properties);

        // Stub JavaMailSender so createMimeMessage returns a real
        // MimeMessage we can inspect. Use a no-op session.
        Session session = Session.getDefaultInstance(new Properties());
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(session));
        when(templateEngine.process(any(String.class), any())).thenReturn("<p>rendered</p>");
    }

    private Ticket newTicket() {
        Ticket t = new Ticket();
        t.setId(42L);
        t.setSubject("Help");
        t.setStatus(TicketStatus.OPEN);
        t.setPriority(TicketPriority.MEDIUM);
        t.setRequesterEmail("alice@example.com");
        return t;
    }

    @Test
    void sendTicketCreatedNotification_setsCanonicalMessageIdAndSignedReplyTo() throws Exception {
        service.sendTicketCreatedNotification(newTicket());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        String[] messageId = sent.getHeader("Message-ID");
        assertThat(messageId).isNotNull();
        assertThat(messageId[0]).isEqualTo("<ticket-42@support.example.com>");

        // No In-Reply-To on the initial notification (thread anchor).
        assertThat(sent.getHeader("In-Reply-To")).isNull();

        String[] replyTo = sent.getHeader("Reply-To");
        assertThat(replyTo).isNotNull();
        assertThat(replyTo[0]).matches("reply\\+42\\.[a-f0-9]{8}@support\\.example\\.com");
    }

    @Test
    void sendReplyNotification_addsInReplyToAndReferencesPointingToTicketRoot() throws Exception {
        Ticket ticket = newTicket();
        AgentProfile agent = new AgentProfile();
        agent.setEmail("agent@example.com");
        ticket.setAssignedAgent(agent);

        Reply reply = new Reply();
        reply.setId(7L);
        reply.setAuthorType("requester"); // requester reply → goes to agent
        reply.setTicket(ticket);

        service.sendReplyNotification(ticket, reply);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getHeader("Message-ID")[0])
                .isEqualTo("<ticket-42-reply-7@support.example.com>");
        assertThat(sent.getHeader("In-Reply-To")[0])
                .isEqualTo("<ticket-42@support.example.com>");
        assertThat(sent.getHeader("References")[0])
                .isEqualTo("<ticket-42@support.example.com>");
    }

    @Test
    void sendEmail_whenInboundSecretBlank_doesNotSetReplyTo() throws Exception {
        properties.getEmail().setInboundSecret("");

        service.sendTicketCreatedNotification(newTicket());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        // Message-ID still present; Reply-To omitted when we have no key.
        assertThat(sent.getHeader("Message-ID")).isNotNull();
        assertThat(sent.getHeader("Reply-To")).isNull();
    }

    @Test
    void sendSatisfactionSurvey_doesNotSetMessageId_butStillSetsReplyTo() throws Exception {
        service.sendSatisfactionSurvey(newTicket(), "https://example.com/survey");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        // Survey is not part of the ticket thread — no Message-ID /
        // In-Reply-To chain needed. But the Reply-To still routes back
        // to the ticket so replies (e.g. "what about this other issue")
        // land on the right ticket id.
        assertThat(sent.getHeader("Message-ID")).isNull();
        assertThat(sent.getHeader("In-Reply-To")).isNull();
        assertThat(sent.getHeader("Reply-To")).isNotNull();
    }
}
