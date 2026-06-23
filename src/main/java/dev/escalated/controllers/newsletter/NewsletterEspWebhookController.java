package dev.escalated.controllers.newsletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.services.newsletter.NewsletterTracker;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/webhooks/newsletter")
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class NewsletterEspWebhookController {

    private final NewsletterTracker tracker;
    private final ObjectMapper objectMapper;

    public NewsletterEspWebhookController(NewsletterTracker tracker, ObjectMapper objectMapper) {
        this.tracker = tracker;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/postmark")
    public ResponseEntity<Map<String, Boolean>> postmark(@RequestBody Map<String, Object> body) {
        String token = tokenFromMessageId(String.valueOf(body.getOrDefault("MessageID", "")));
        switch (String.valueOf(body.getOrDefault("RecordType", ""))) {
            case "Open" -> tracker.recordOpen(token);
            case "Click" -> tracker.recordClick(token, String.valueOf(body.getOrDefault("OriginalLink", "")));
            case "Bounce" -> tracker.recordBounce(
                    token,
                    hardPostmark(String.valueOf(body.getOrDefault("Type", ""))) ? "hard" : "soft",
                    String.valueOf(body.getOrDefault("Description", "")));
            case "SpamComplaint" -> tracker.recordComplaint(token);
            default -> { }
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/mailgun")
    public ResponseEntity<Map<String, Boolean>> mailgun(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> eventData = (Map<String, Object>) body.getOrDefault("event-data", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) ((Map<String, Object>) eventData
                        .getOrDefault("message", Map.of()))
                .getOrDefault("headers", Map.of());
        String token = tokenFromMessageId(String.valueOf(headers.getOrDefault("message-id", "")));
        switch (String.valueOf(eventData.getOrDefault("event", ""))) {
            case "opened" -> tracker.recordOpen(token);
            case "clicked" -> tracker.recordClick(token, String.valueOf(eventData.getOrDefault("url", "")));
            case "failed" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> deliveryStatus = (Map<String, Object>) eventData.getOrDefault("delivery-status", Map.of());
                tracker.recordBounce(
                        token,
                        "permanent".equals(eventData.get("severity")) ? "hard" : "soft",
                        String.valueOf(deliveryStatus.getOrDefault("description", "")));
            }
            case "complained" -> tracker.recordComplaint(token);
            default -> { }
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/ses")
    public ResponseEntity<Map<String, Boolean>> ses(@RequestBody Map<String, Object> body) throws Exception {
        Object messageRaw = body.get("Message");
        JsonNode message;
        if (messageRaw instanceof String str) {
            message = objectMapper.readTree(str);
        } else {
            message = objectMapper.valueToTree(messageRaw == null ? body : messageRaw);
        }
        String token = tokenFromMessageId(message.path("mail").path("messageId").asText(""));
        switch (message.path("eventType").asText("")) {
            case "Open" -> tracker.recordOpen(token);
            case "Click" -> tracker.recordClick(token, message.path("click").path("link").asText(""));
            case "Bounce" -> tracker.recordBounce(
                    token,
                    "Permanent".equals(message.path("bounce").path("bounceType").asText("")) ? "hard" : "soft",
                    message.path("bounce").path("bounceSubType").asText(""));
            case "Complaint" -> tracker.recordComplaint(token);
            default -> { }
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/sendgrid")
    public ResponseEntity<Map<String, Boolean>> sendgrid(@RequestBody Object body) {
        JsonNode events = objectMapper.valueToTree(body);
        if (!events.isArray()) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        for (JsonNode event : events) {
            String token = tokenFromMessageId(firstText(event, "smtp-id", "sg_message_id"));
            switch (event.path("event").asText("")) {
                case "open" -> tracker.recordOpen(token);
                case "click" -> tracker.recordClick(token, event.path("url").asText(""));
                case "bounce" -> tracker.recordBounce(
                        token,
                        "blocked".equals(event.path("type").asText("")) ? "hard" : "soft",
                        event.path("reason").asText(""));
                case "dropped" -> tracker.recordBounce(token, "hard", event.path("reason").asText(""));
                case "spamreport" -> tracker.recordComplaint(token);
                default -> { }
            }
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static final Set<String> HARD_POSTMARK = Set.of("HardBounce", "BadEmailAddress", "BlockedRecipient");

    private static boolean hardPostmark(String type) {
        return HARD_POSTMARK.contains(type);
    }

    private static String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asText("");
            }
        }
        return "";
    }

    private static String tokenFromMessageId(String messageId) {
        java.util.regex.Matcher matched = java.util.regex.Pattern
                .compile("n-\\d+-([A-Za-z0-9]+)@")
                .matcher(messageId);
        if (matched.find()) {
            return matched.group(1);
        }
        String local = messageId.split("@")[0];
        java.util.regex.Matcher localMatched = java.util.regex.Pattern
                .compile("^n-\\d+-([A-Za-z0-9]+)$")
                .matcher(local);
        return localMatched.find() ? localMatched.group(1) : "";
    }
}
