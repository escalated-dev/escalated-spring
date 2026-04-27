package dev.escalated.services.email.inbound;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Parses Mailgun's inbound webhook payload into an
 * {@link InboundMessage}. Mailgun POSTs {@code multipart/form-data}
 * with snake-case field names: {@code sender}, {@code recipient},
 * {@code subject}, {@code body-plain}, {@code body-html},
 * {@code Message-Id}, {@code In-Reply-To}, {@code References}, plus
 * a JSON-encoded {@code attachments} field.
 *
 * <p>The Spring controller already ingests a
 * {@code Map<String,Object>} from the webhook body, so this parser
 * just reads from that map.
 *
 * <p>Mailgun hosts attachment content behind provider URLs (for
 * large attachments); we carry the URL through in
 * {@link InboundAttachment#downloadUrl} so a follow-up worker can
 * fetch + persist out-of-band.
 */
@Component
public class MailgunInboundParser implements InboundEmailParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "mailgun";
    }

    @Override
    public InboundMessage parse(Object rawPayload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = rawPayload instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : MAPPER.convertValue(rawPayload, new TypeReference<Map<String, Object>>() {});

        String fromEmail = stringAt(payload, "sender");
        if (fromEmail == null || fromEmail.isEmpty()) {
            fromEmail = stringAt(payload, "from");
        }
        String fromName = extractFromName(stringAt(payload, "from"));

        String toEmail = stringAt(payload, "recipient");
        if (toEmail == null || toEmail.isEmpty()) {
            toEmail = stringAt(payload, "To");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        putIfNonEmpty(headers, "Message-ID", stringAt(payload, "Message-Id"));
        putIfNonEmpty(headers, "In-Reply-To", stringAt(payload, "In-Reply-To"));
        putIfNonEmpty(headers, "References", stringAt(payload, "References"));

        return new InboundMessage(
                fromEmail == null ? "" : fromEmail,
                fromName,
                toEmail == null ? "" : toEmail,
                stringAt(payload, "subject"),
                stringAt(payload, "body-plain"),
                stringAt(payload, "body-html"),
                stringAt(payload, "Message-Id"),
                stringAt(payload, "In-Reply-To"),
                stringAt(payload, "References"),
                headers,
                extractAttachments(stringAt(payload, "attachments"))
        );
    }

    private static String stringAt(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v == null ? null : v.toString();
    }

    private static void putIfNonEmpty(Map<String, String> headers, String key, String value) {
        if (value != null && !value.isEmpty()) {
            headers.put(key, value);
        }
    }

    /**
     * Mailgun's {@code from} field is typically
     * {@code "Full Name <email@host>"} — extract the display name
     * portion. Returns {@code null} when the input has no angle-
     * bracketed email (bare email address).
     */
    private static String extractFromName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        int angle = raw.indexOf('<');
        if (angle <= 0) {
            return null;
        }
        String name = raw.substring(0, angle).trim();
        // Strip surrounding quotes if present.
        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
            name = name.substring(1, name.length() - 1);
        }
        return name.isEmpty() ? null : name;
    }

    private static List<InboundAttachment> extractAttachments(String attachmentsJson) {
        if (attachmentsJson == null || attachmentsJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> entries = MAPPER.readValue(
                    attachmentsJson, new TypeReference<List<Map<String, Object>>>() {});
            List<InboundAttachment> list = new ArrayList<>();
            for (Map<String, Object> entry : entries) {
                String name = stringAt(entry, "name");
                String contentType = stringAt(entry, "content-type");
                Long size = entry.get("size") instanceof Number n ? n.longValue() : null;
                String url = stringAt(entry, "url");
                list.add(new InboundAttachment(
                        name == null ? "attachment" : name,
                        contentType == null ? "application/octet-stream" : contentType,
                        size,
                        null,
                        url
                ));
            }
            return list;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }
}
