package dev.escalated.services;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.services.email.MessageIdUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EscalatedProperties properties;

    public EmailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            EscalatedProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    public void sendTicketCreatedNotification(Ticket ticket) {
        Context context = new Context();
        context.setVariable("ticket", ticket);
        context.setVariable("ticketUrl", "/escalated/tickets/" + ticket.getId());

        String htmlContent = templateEngine.process("email/ticket-created", context);
        String messageId = MessageIdUtil.buildMessageId(
                ticket.getId(), null, emailDomain());
        sendEmail(ticket.getRequesterEmail(), "Ticket Created: " + ticket.getSubject(),
                htmlContent, messageId, null, ticket.getId());
    }

    public void sendReplyNotification(Ticket ticket, Reply reply) {
        Context context = new Context();
        context.setVariable("ticket", ticket);
        context.setVariable("reply", reply);

        String htmlContent = templateEngine.process("email/ticket-reply", context);
        String recipient = "agent".equals(reply.getAuthorType())
                ? ticket.getRequesterEmail()
                : (ticket.getAssignedAgent() != null ? ticket.getAssignedAgent().getEmail() : null);

        if (recipient != null) {
            // Reply Message-ID includes reply id; References points back
            // to the ticket-root Message-ID so clients thread correctly.
            String replyMessageId = MessageIdUtil.buildMessageId(
                    ticket.getId(), reply.getId(), emailDomain());
            String rootMessageId = MessageIdUtil.buildMessageId(
                    ticket.getId(), null, emailDomain());
            sendEmail(recipient, "Re: " + ticket.getSubject(), htmlContent,
                    replyMessageId, rootMessageId, ticket.getId());
        }
    }

    public void sendSatisfactionSurvey(Ticket ticket, String surveyUrl) {
        Context context = new Context();
        context.setVariable("ticket", ticket);
        context.setVariable("surveyUrl", surveyUrl);

        String htmlContent = templateEngine.process("email/satisfaction-survey", context);
        sendEmail(ticket.getRequesterEmail(), "How was your experience?",
                htmlContent, null, null, ticket.getId());
    }

    /**
     * Internal: send a MIME message with canonical threading + Reply-To
     * headers. {@code messageId} is the header to set; {@code threadRoot}
     * (nullable) is the In-Reply-To / References anchor for replies.
     * {@code ticketId} is used to compute the signed Reply-To so the
     * inbound provider webhook can verify ticket identity independently
     * of the Message-ID / In-Reply-To chain.
     */
    private void sendEmail(String to, String subject, String htmlContent,
                           String messageId, String threadRoot, long ticketId) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            if (messageId != null) {
                message.setHeader("Message-ID", messageId);
            }
            if (threadRoot != null) {
                message.setHeader("In-Reply-To", threadRoot);
                message.setHeader("References", threadRoot);
            }

            // Signed Reply-To so the inbound webhook can verify ticket
            // identity even when clients strip the Message-ID chain.
            String secret = emailInboundSecret();
            if (!secret.isEmpty()) {
                helper.setReplyTo(
                        MessageIdUtil.buildReplyTo(ticketId, secret, emailDomain()));
            }

            mailSender.send(message);
        } catch (MessagingException ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }

    private String emailDomain() {
        String domain = properties.getEmail().getDomain();
        return (domain == null || domain.isBlank()) ? "localhost" : domain;
    }

    private String emailInboundSecret() {
        String secret = properties.getEmail().getInboundSecret();
        return secret == null ? "" : secret;
    }
}
