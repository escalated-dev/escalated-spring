package dev.escalated.services;

import dev.escalated.config.EscalatedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * The default {@link GuestCodeMailer}: a plain-text email through the host's
 * mail sender. A failure is logged, never reported to the requester, so the
 * answer does not reveal whether delivery worked.
 */
@Component
public class MailGuestCodeMailer implements GuestCodeMailer {

    private static final Logger log = LoggerFactory.getLogger(MailGuestCodeMailer.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final EscalatedProperties properties;

    public MailGuestCodeMailer(ObjectProvider<JavaMailSender> mailSender, EscalatedProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String email, String code, String purpose, int expiresInMinutes) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("No JavaMailSender is configured; a guest verification code could not be sent.");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        String from = properties.getGuestAccess().getFromAddress();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(email);
        message.setSubject("Your verification code");
        message.setText("Your verification code is " + code + ".\n\n"
                + "It expires in " + expiresInMinutes + " minutes. If you did not ask for it, ignore this email.");
        try {
            sender.send(message);
        } catch (RuntimeException ex) {
            log.error("Failed to send a guest verification code: {}", ex.getMessage());
        }
    }
}
