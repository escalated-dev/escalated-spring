package dev.escalated.services.email.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Parses Postmark's inbound webhook payload into an
 * {@link InboundMessage}. Postmark POSTs a JSON body with
 * {@code FromFull} / {@code ToFull} / {@code Subject} / {@code TextBody}
 * / {@code HtmlBody} / {@code Headers} / {@code Attachments} fields.
 *
 * <p>Register additional providers (Mailgun, SES) by implementing
 * {@link InboundEmailParser} as Spring {@code @Component}s — the
 * controller selects by {@link #name()}.
 */
@Component
public class PostmarkInboundParser implements InboundEmailParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "postmark";
    }

    @Override
    public InboundMessage parse(Object rawPayload) {
        JsonNode root = MAPPER.valueToTree(rawPayload);

        JsonNode fromFull = root.path("FromFull");
        String fromEmail = fromFull.path("Email").asText(root.path("From").asText(""));
        String fromName = fromFull.path("Name").asText(null);

        String toEmail = root.path("OriginalRecipient").asText(null);
        if (toEmail == null || toEmail.isEmpty()) {
            toEmail = firstToEmail(root);
        }
        if (toEmail == null || toEmail.isEmpty()) {
            toEmail = root.path("To").asText("");
        }

        Map<String, String> headers = extractHeaders(root);

        return new InboundMessage(
                fromEmail,
                fromName,
                toEmail,
                root.path("Subject").asText(""),
                textOrNull(root, "TextBody"),
                textOrNull(root, "HtmlBody"),
                firstNonEmpty(textOrNull(root, "MessageID"), headers.get("Message-ID")),
                headers.get("In-Reply-To"),
                headers.get("References"),
                headers,
                extractAttachments(root)
        );
    }

    private static String firstToEmail(JsonNode root) {
        JsonNode toFull = root.path("ToFull");
        if (!toFull.isArray()) {
            return null;
        }
        for (JsonNode entry : toFull) {
            String email = entry.path("Email").asText(null);
            if (email != null && !email.isEmpty()) {
                return email;
            }
        }
        return null;
    }

    private static Map<String, String> extractHeaders(JsonNode root) {
        Map<String, String> headers = new LinkedHashMap<>();
        JsonNode arr = root.path("Headers");
        if (!arr.isArray()) {
            return headers;
        }
        for (JsonNode entry : arr) {
            String name = entry.path("Name").asText(null);
            String value = entry.path("Value").asText(null);
            if (name != null && !name.isEmpty() && value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    private static List<InboundAttachment> extractAttachments(JsonNode root) {
        List<InboundAttachment> list = new ArrayList<>();
        JsonNode arr = root.path("Attachments");
        if (!arr.isArray()) {
            return list;
        }
        for (JsonNode entry : arr) {
            String name = entry.path("Name").asText("attachment");
            String contentType = entry.path("ContentType").asText("application/octet-stream");
            Long size = entry.has("ContentLength") ? entry.path("ContentLength").asLong() : null;
            String contentBase64 = textOrNull(entry, "Content");
            byte[] content = contentBase64 != null ? Base64.getDecoder().decode(contentBase64) : null;
            list.add(new InboundAttachment(name, contentType, size, content, null));
        }
        return list;
    }

    private static String textOrNull(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }
}
