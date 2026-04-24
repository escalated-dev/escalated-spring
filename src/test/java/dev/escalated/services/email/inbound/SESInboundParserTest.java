package dev.escalated.services.email.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SESInboundParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final SESInboundParser parser = new SESInboundParser();

    @Test
    void nameIsSes() {
        assertThat(parser.name()).isEqualTo("ses");
    }

    @Test
    void subscriptionConfirmationThrowsWithSubscribeUrl() {
        var envelope = Map.of(
                "Type", "SubscriptionConfirmation",
                "TopicArn", "arn:aws:sns:us-east-1:123:escalated-inbound",
                "SubscribeURL", "https://sns.us-east-1.amazonaws.com/?Action=ConfirmSubscription&Token=x",
                "Token", "abc"
        );

        assertThatExceptionOfType(SESSubscriptionConfirmationException.class)
                .isThrownBy(() -> parser.parse(envelope))
                .matches(ex -> ex.getTopicArn().equals("arn:aws:sns:us-east-1:123:escalated-inbound"))
                .matches(ex -> ex.getSubscribeUrl().contains("ConfirmSubscription"))
                .matches(ex -> ex.getToken().equals("abc"));
    }

    @Test
    void notificationExtractsThreadingMetadata() throws Exception {
        var sesMessage = Map.of(
                "notificationType", "Received",
                "mail", Map.of(
                        "source", "alice@example.com",
                        "destination", List.of("support@example.com"),
                        "headers", List.of(
                                Map.of("name", "From", "value", "Alice <alice@example.com>"),
                                Map.of("name", "To", "value", "support@example.com"),
                                Map.of("name", "Subject", "value", "[ESC-42] Re: Help"),
                                Map.of("name", "Message-ID", "value", "<external-xyz@mail.alice.com>"),
                                Map.of("name", "In-Reply-To", "value", "<ticket-42@support.example.com>"),
                                Map.of("name", "References", "value", "<ticket-42@support.example.com> <prev@mail.com>")
                        ),
                        "commonHeaders", Map.of(
                                "from", List.of("Alice <alice@example.com>"),
                                "to", List.of("support@example.com"),
                                "subject", "[ESC-42] Re: Help"
                        )
                )
        );
        var envelope = Map.of(
                "Type", "Notification",
                "Message", MAPPER.writeValueAsString(sesMessage)
        );

        InboundMessage msg = parser.parse(envelope);

        assertThat(msg.fromEmail()).isEqualTo("alice@example.com");
        assertThat(msg.fromName()).isEqualTo("Alice");
        assertThat(msg.toEmail()).isEqualTo("support@example.com");
        assertThat(msg.subject()).isEqualTo("[ESC-42] Re: Help");
        assertThat(msg.messageId()).isEqualTo("<external-xyz@mail.alice.com>");
        assertThat(msg.inReplyTo()).isEqualTo("<ticket-42@support.example.com>");
        assertThat(msg.references()).contains("ticket-42@support.example.com");
        assertThat(msg.headers()).containsEntry("From", "Alice <alice@example.com>");
    }

    @Test
    void notificationDecodesPlainTextBody() throws Exception {
        String rawMime = "From: alice@example.com\r\n"
                + "To: support@example.com\r\n"
                + "Subject: Hi\r\n"
                + "Content-Type: text/plain; charset=\"utf-8\"\r\n"
                + "\r\n"
                + "This is the plain text body.";
        String contentB64 = Base64.getEncoder().encodeToString(rawMime.getBytes());

        var envelope = Map.of(
                "Type", "Notification",
                "Message", MAPPER.writeValueAsString(Map.of(
                        "mail", Map.of(
                                "commonHeaders", Map.of(
                                        "from", List.of("alice@example.com"),
                                        "to", List.of("support@example.com"),
                                        "subject", "Hi")),
                        "content", contentB64
                ))
        );

        InboundMessage msg = parser.parse(envelope);

        assertThat(msg.bodyText()).contains("This is the plain text body.");
    }

    @Test
    void notificationDecodesMultipartBody() throws Exception {
        String boundary = "boundary-abc";
        String rawMime = "From: alice@example.com\r\n"
                + "To: support@example.com\r\n"
                + "Subject: Hi\r\n"
                + "Content-Type: multipart/alternative; boundary=\"" + boundary + "\"\r\n"
                + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: text/plain; charset=\"utf-8\"\r\n"
                + "\r\n"
                + "Plain body\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: text/html; charset=\"utf-8\"\r\n"
                + "\r\n"
                + "<p>HTML body</p>\r\n"
                + "--" + boundary + "--\r\n";
        String contentB64 = Base64.getEncoder().encodeToString(rawMime.getBytes());

        var envelope = Map.of(
                "Type", "Notification",
                "Message", MAPPER.writeValueAsString(Map.of(
                        "mail", Map.of(
                                "commonHeaders", Map.of(
                                        "from", List.of("alice@example.com"),
                                        "to", List.of("support@example.com"),
                                        "subject", "Hi")),
                        "content", contentB64
                ))
        );

        InboundMessage msg = parser.parse(envelope);

        assertThat(msg.bodyText()).contains("Plain body");
        assertThat(msg.bodyHtml()).contains("<p>HTML body</p>");
    }

    @Test
    void notificationMissingContentLeavesBodyEmpty() throws Exception {
        var envelope = Map.of(
                "Type", "Notification",
                "Message", MAPPER.writeValueAsString(Map.of(
                        "mail", Map.of(
                                "commonHeaders", Map.of(
                                        "from", List.of("alice@example.com"),
                                        "to", List.of("support@example.com"),
                                        "subject", "Hi"))
                ))
        );

        InboundMessage msg = parser.parse(envelope);

        assertThat(msg.bodyText()).isNull();
        assertThat(msg.bodyHtml()).isNull();
        assertThat(msg.fromEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void unknownEnvelopeTypeThrows() {
        assertThatThrownBy(() -> parser.parse(Map.of("Type", "UnknownType")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported SNS envelope type");
    }

    @Test
    void missingMessageFieldThrows() {
        assertThatThrownBy(() -> parser.parse(Map.of("Type", "Notification")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no Message body");
    }

    @Test
    void malformedMessageJsonThrows() {
        assertThatThrownBy(() -> parser.parse(Map.of(
                "Type", "Notification",
                "Message", "not json at all")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fallsBackToHeadersArrayForThreadingFields() throws Exception {
        var envelope = Map.of(
                "Type", "Notification",
                "Message", MAPPER.writeValueAsString(Map.of(
                        "mail", Map.of(
                                "headers", List.of(
                                        Map.of("name", "Message-ID", "value", "<fallback@mail.com>"),
                                        Map.of("name", "In-Reply-To", "value", "<ticket-99@support.example.com>")
                                ),
                                "commonHeaders", Map.of(
                                        "from", List.of("alice@example.com"),
                                        "to", List.of("support@example.com"),
                                        "subject", "Fallback"))
                ))
        );

        InboundMessage msg = parser.parse(envelope);

        assertThat(msg.messageId()).isEqualTo("<fallback@mail.com>");
        assertThat(msg.inReplyTo()).isEqualTo("<ticket-99@support.example.com>");
    }

    @Test
    void acceptsRawJsonString() throws Exception {
        String bodyJson = MAPPER.writeValueAsString(Map.of(
                "Type", "Notification",
                "Message", MAPPER.writeValueAsString(Map.of(
                        "mail", Map.of(
                                "commonHeaders", Map.of(
                                        "from", List.of("alice@example.com"),
                                        "to", List.of("support@example.com"),
                                        "subject", "Hi"))
                ))
        ));

        InboundMessage msg = parser.parse(bodyJson);

        assertThat(msg.fromEmail()).isEqualTo("alice@example.com");
    }
}
