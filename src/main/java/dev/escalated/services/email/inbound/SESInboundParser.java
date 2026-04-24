package dev.escalated.services.email.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.stereotype.Component;

/**
 * Parses AWS SES inbound mail delivered via SNS HTTP subscription.
 * SES receipt rules publish to an SNS topic; host apps subscribe via
 * HTTP and SNS POSTs the envelope to the unified
 * {@code /escalated/webhook/email/inbound?adapter=ses} webhook.
 *
 * <p>Handles two SNS envelope types:
 * <ul>
 *   <li>{@code Type=SubscriptionConfirmation} — throws
 *     {@link SESSubscriptionConfirmationException} carrying the
 *     {@code SubscribeURL} that the host must GET out-of-band to
 *     activate the subscription.</li>
 *   <li>{@code Type=Notification} — parses the JSON-encoded
 *     {@code Message} field for {@code mail.commonHeaders}
 *     (from/to/subject) and the {@code mail.headers} array
 *     (Message-ID / In-Reply-To / References). Falls back to
 *     {@code mail.headers} when {@code commonHeaders} doesn't
 *     surface a threading field.</li>
 * </ul>
 *
 * <p>Best-effort MIME body extraction from the base64-encoded
 * {@code content} field when SES is configured with
 * {@code action.type=SNS} / {@code encoding=BASE64}. Uses
 * {@code jakarta.mail} — already transitively available via
 * Spring Boot's mail starter. Handles single-part {@code text/plain} +
 * {@code text/html} and {@code multipart/alternative} bodies.
 */
@Component
public class SESInboundParser implements InboundEmailParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "ses";
    }

    @Override
    public InboundMessage parse(Object rawPayload) {
        JsonNode envelope = toJson(rawPayload);
        String snsType = text(envelope, "Type");

        switch (snsType) {
            case "SubscriptionConfirmation" ->
                throw new SESSubscriptionConfirmationException(
                        text(envelope, "TopicArn"),
                        text(envelope, "SubscribeURL"),
                        text(envelope, "Token"));
            case "Notification" -> { /* fall through */ }
            default -> throw new IllegalArgumentException(
                    "Unsupported SNS envelope type: " + snsType);
        }

        String messageJson = text(envelope, "Message");
        if (messageJson.isEmpty()) {
            throw new IllegalArgumentException("SES notification has no Message body");
        }

        JsonNode notification;
        try {
            notification = MAPPER.readTree(messageJson);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "SES notification Message is not valid JSON", e);
        }

        JsonNode mail = notification.path("mail");
        JsonNode common = mail.path("commonHeaders");

        FromAddress from = parseFirstAddressList(common.path("from"));
        FromAddress to = parseFirstAddressList(common.path("to"));

        String subject = text(common, "subject");
        String messageId = text(common, "messageId");
        String inReplyTo = text(common, "inReplyTo");
        String references = text(common, "references");

        Map<String, String> headers = extractHeaders(mail);
        if (messageId.isEmpty()) {
            messageId = headers.getOrDefault("Message-ID", "");
        }
        if (inReplyTo.isEmpty()) {
            inReplyTo = headers.getOrDefault("In-Reply-To", "");
        }
        if (references.isEmpty()) {
            references = headers.getOrDefault("References", "");
        }

        BodyParts body = extractBody(notification.path("content").asText(""));

        return new InboundMessage(
                from.email(),
                from.name(),
                to.email(),
                subject,
                body.text(),
                body.html(),
                messageId.isEmpty() ? null : messageId,
                inReplyTo.isEmpty() ? null : inReplyTo,
                references.isEmpty() ? null : references,
                headers,
                null
        );
    }

    private static JsonNode toJson(Object rawPayload) {
        try {
            if (rawPayload instanceof JsonNode node) {
                return node;
            }
            if (rawPayload instanceof String str) {
                return MAPPER.readTree(str);
            }
            if (rawPayload instanceof byte[] bytes) {
                return MAPPER.readTree(bytes);
            }
            return MAPPER.valueToTree(rawPayload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unparseable SES payload", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }

    private record FromAddress(String email, String name) {}

    /**
     * SES's {@code commonHeaders.from} / {@code .to} are arrays of
     * RFC 5322 strings like {@code ["Alice <alice@example.com>"]}.
     * Returns the first entry's email + display name.
     */
    private static FromAddress parseFirstAddressList(JsonNode arr) {
        if (!arr.isArray() || arr.isEmpty()) {
            return new FromAddress("", null);
        }
        String raw = arr.get(0).asText("").trim();
        if (raw.isEmpty()) {
            return new FromAddress("", null);
        }
        try {
            InternetAddress addr = new InternetAddress(raw, false);
            return new FromAddress(
                    addr.getAddress() == null ? "" : addr.getAddress(),
                    addr.getPersonal()
            );
        } catch (Exception e) {
            return new FromAddress(raw, null);
        }
    }

    private static Map<String, String> extractHeaders(JsonNode mail) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode arr = mail.path("headers");
        if (!arr.isArray()) {
            return out;
        }
        for (JsonNode entry : arr) {
            String name = text(entry, "name");
            String value = text(entry, "value");
            if (!name.isEmpty()) {
                out.put(name, value);
            }
        }
        return out;
    }

    private record BodyParts(String text, String html) {}

    /**
     * Decode the base64 {@code content} field and extract
     * {@code text/plain} + {@code text/html} parts via jakarta.mail.
     * Returns empty strings when content is absent, malformed, or
     * the MIME parse fails — the router still resolves via
     * threading metadata regardless.
     */
    private static BodyParts extractBody(String contentBase64) {
        if (contentBase64.isEmpty()) {
            return new BodyParts(null, null);
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException e) {
            return new BodyParts(null, null);
        }

        try {
            Session session = Session.getDefaultInstance(new Properties(), null);
            MimeMessage msg = new MimeMessage(session, new ByteArrayInputStream(raw));
            Object content = msg.getContent();

            if (content instanceof MimeMultipart multipart) {
                return walkMultipart(multipart);
            }
            if (content instanceof String body) {
                String contentType = msg.getContentType();
                if (contentType != null && contentType.toLowerCase().startsWith("text/html")) {
                    return new BodyParts(null, body);
                }
                return new BodyParts(body, null);
            }
            return new BodyParts(null, null);
        } catch (Exception e) {
            return new BodyParts(null, null);
        }
    }

    private static BodyParts walkMultipart(MimeMultipart multipart) throws MessagingException {
        String text = null;
        String html = null;
        for (int i = 0; i < multipart.getCount(); i++) {
            var part = multipart.getBodyPart(i);
            try {
                Object partContent = part.getContent();
                String partType = part.getContentType() == null ? "" : part.getContentType().toLowerCase();
                if (partContent instanceof String s) {
                    if (partType.startsWith("text/plain") && text == null) {
                        text = s;
                    } else if (partType.startsWith("text/html") && html == null) {
                        html = s;
                    }
                } else if (partContent instanceof MimeMultipart nested) {
                    BodyParts nestedParts = walkMultipart(nested);
                    if (text == null) text = nestedParts.text();
                    if (html == null) html = nestedParts.html();
                }
            } catch (Exception ignored) {
                // continue to next part
            }
        }
        return new BodyParts(text, html);
    }
}
