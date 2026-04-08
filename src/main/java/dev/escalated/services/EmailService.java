package dev.escalated.services;

import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
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

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public void sendTicketCreatedNotification(Ticket ticket) {
        Context context = new Context();
        context.setVariable("ticket", ticket);
        context.setVariable("ticketUrl", "/escalated/tickets/" + ticket.getId());

        String htmlContent = templateEngine.process("email/ticket-created", context);
        sendEmail(ticket.getRequesterEmail(), "Ticket Created: " + ticket.getSubject(),
                htmlContent, ticket.getEmailMessageId());
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
            sendEmail(recipient, "Re: " + ticket.getSubject(), htmlContent, reply.getEmailMessageId());
        }
    }

    public void sendSatisfactionSurvey(Ticket ticket, String surveyUrl) {
        Context context = new Context();
        context.setVariable("ticket", ticket);
        context.setVariable("surveyUrl", surveyUrl);

        String htmlContent = templateEngine.process("email/satisfaction-survey", context);
        sendEmail(ticket.getRequesterEmail(), "How was your experience?", htmlContent, null);
    }

    private void sendEmail(String to, String subject, String htmlContent, String messageId) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            if (messageId != null) {
                message.setHeader("Message-ID", messageId);
                message.setHeader("References", messageId);
            }

            mailSender.send(message);
        } catch (MessagingException ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }
}
