package dev.escalated.services.email.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Parser-equivalence tests: the same logical email, expressed in each
 * provider's native webhook payload shape, should normalize to the
 * same {@link InboundMessage} metadata. Parser equivalence at this
 * layer guarantees a reply delivered via any provider routes to the
 * same ticket via the same threading chain.
 *
 * <p>Mirrors escalated-go#37 + escalated-dotnet#31. Adding a fourth
 * provider in the future can reuse the same {@code LogicalEmail} →
 * provider-payload builders and get contract validation for free.
 */
class ParserEquivalenceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record LogicalEmail(
            String fromEmail,
            String fromName,
            String toEmail,
            String subject,
            String bodyText,
            String messageId,
            String inReplyTo,
            String references) {}

    private static final LogicalEmail SAMPLE = new LogicalEmail(
            "alice@example.com",
            "Alice",
            "support@example.com",
            "Re: Help with invoice",
            "Thanks for the quick response.",
            "<external-reply-xyz@mail.alice.com>",
            "<ticket-42@support.example.com>",
            "<ticket-42@support.example.com>"
    );

    private static Map<String, Object> postmarkPayload(LogicalEmail e) {
        return Map.of(
                "FromFull", Map.of("Email", e.fromEmail, "Name", e.fromName),
                "To", e.toEmail,
                "Subject", e.subject,
                "TextBody", e.bodyText,
                "Headers", List.of(
                        Map.of("Name", "Message-ID", "Value", e.messageId),
                        Map.of("Name", "In-Reply-To", "Value", e.inReplyTo),
                        Map.of("Name", "References", "Value", e.references)
                )
        );
    }

    private static Map<String, Object> mailgunPayload(LogicalEmail e) {
        return Map.of(
                "sender", e.fromEmail,
                "from", e.fromName + " <" + e.fromEmail + ">",
                "recipient", e.toEmail,
                "subject", e.subject,
                "body-plain", e.bodyText,
                "Message-Id", e.messageId,
                "In-Reply-To", e.inReplyTo,
                "References", e.references
        );
    }

    private static Map<String, Object> sesPayload(LogicalEmail e) throws Exception {
        // Include full raw MIME as base64 so body extraction is
        // exercised — keeps the test payload close to a real SES
        // delivery.
        String mime = "From: " + e.fromName + " <" + e.fromEmail + ">\r\n"
                + "To: " + e.toEmail + "\r\n"
                + "Subject: " + e.subject + "\r\n"
                + "Message-ID: " + e.messageId + "\r\n"
                + "In-Reply-To: " + e.inReplyTo + "\r\n"
                + "References: " + e.references + "\r\n"
                + "Content-Type: text/plain; charset=\"utf-8\"\r\n"
                + "\r\n"
                + e.bodyText;

        var sesMessage = Map.of(
                "notificationType", "Received",
                "mail", Map.of(
                        "source", e.fromEmail,
                        "destination", List.of(e.toEmail),
                        "headers", List.of(
                                Map.of("name", "From", "value", e.fromName + " <" + e.fromEmail + ">"),
                                Map.of("name", "To", "value", e.toEmail),
                                Map.of("name", "Subject", "value", e.subject),
                                Map.of("name", "Message-ID", "value", e.messageId),
                                Map.of("name", "In-Reply-To", "value", e.inReplyTo),
                                Map.of("name", "References", "value", e.references)
                        ),
                        "commonHeaders", Map.of(
                                "from", List.of(e.fromName + " <" + e.fromEmail + ">"),
                                "to", List.of(e.toEmail),
                                "subject", e.subject
                        )
                ),
                "content", Base64.getEncoder().encodeToString(mime.getBytes())
        );
        return Map.of(
                "Type", "Notification",
                "Message", MAPPER.writeValueAsString(sesMessage)
        );
    }

    @Test
    void normalizesToSameMessage() throws Exception {
        InboundMessage postmark = new PostmarkInboundParser().parse(postmarkPayload(SAMPLE));
        InboundMessage mailgun = new MailgunInboundParser().parse(mailgunPayload(SAMPLE));
        InboundMessage ses = new SESInboundParser().parse(sesPayload(SAMPLE));

        for (var entry : Map.of(
                "postmark", postmark,
                "mailgun", mailgun,
                "ses", ses).entrySet()) {
            String name = entry.getKey();
            InboundMessage msg = entry.getValue();

            assertThat(msg.fromEmail()).as("%s: fromEmail", name).isEqualTo(SAMPLE.fromEmail);
            assertThat(msg.toEmail()).as("%s: toEmail", name).isEqualTo(SAMPLE.toEmail);
            assertThat(msg.subject()).as("%s: subject", name).isEqualTo(SAMPLE.subject);
            assertThat(msg.inReplyTo()).as("%s: inReplyTo", name).isEqualTo(SAMPLE.inReplyTo);
            assertThat(msg.references()).as("%s: references", name).isEqualTo(SAMPLE.references);
        }
    }

    @Test
    void bodyExtractionMatches() throws Exception {
        InboundMessage postmark = new PostmarkInboundParser().parse(postmarkPayload(SAMPLE));
        InboundMessage mailgun = new MailgunInboundParser().parse(mailgunPayload(SAMPLE));
        InboundMessage ses = new SESInboundParser().parse(sesPayload(SAMPLE));

        assertThat(postmark.bodyText()).isEqualTo(SAMPLE.bodyText);
        assertThat(mailgun.bodyText()).isEqualTo(SAMPLE.bodyText);
        assertThat(ses.bodyText()).isEqualTo(SAMPLE.bodyText);
    }
}
