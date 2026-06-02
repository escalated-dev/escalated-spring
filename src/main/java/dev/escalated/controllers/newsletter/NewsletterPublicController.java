package dev.escalated.controllers.newsletter;

import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.decodeTrackedUrl;

import dev.escalated.models.Contact;
import dev.escalated.models.newsletter.Newsletter;
import dev.escalated.models.newsletter.NewsletterDelivery;
import dev.escalated.models.newsletter.NewsletterTemplate;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.NewsletterDeliveryRepository;
import dev.escalated.repositories.NewsletterRepository;
import dev.escalated.repositories.NewsletterTemplateRepository;
import dev.escalated.services.newsletter.NewsletterRenderer;
import dev.escalated.services.newsletter.NewsletterTracker;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/n")
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class NewsletterPublicController {

    private static final byte[] PIXEL_BYTES = Base64.getDecoder().decode(
            "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000d49444154789c63fcffff3f030005fe02fedccc59e70000000049454e44ae426082");

    private static final Map<String, RateWindow> UNSUBSCRIBE_ATTEMPTS = new ConcurrentHashMap<>();

    private final NewsletterTracker tracker;
    private final NewsletterRenderer renderer;
    private final NewsletterDeliveryRepository deliveries;
    private final NewsletterRepository newsletters;
    private final NewsletterTemplateRepository templates;
    private final ContactRepository contacts;

    public NewsletterPublicController(
            NewsletterTracker tracker,
            NewsletterRenderer renderer,
            NewsletterDeliveryRepository deliveries,
            NewsletterRepository newsletters,
            NewsletterTemplateRepository templates,
            ContactRepository contacts) {
        this.tracker = tracker;
        this.renderer = renderer;
        this.deliveries = deliveries;
        this.newsletters = newsletters;
        this.templates = templates;
        this.contacts = contacts;
    }

    @GetMapping("/o/{token}")
    public ResponseEntity<byte[]> open(@PathVariable String token) {
        String clean = token.replaceAll("\\.(gif|png|jpg)$", "");
        tracker.recordOpen(clean);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .body(PIXEL_BYTES);
    }

    @GetMapping("/c/{token}")
    public ResponseEntity<Void> click(@PathVariable String token, @RequestParam(name = "u", defaultValue = "") String encoded) {
        String destination = decodeTrackedUrl(encoded);
        tracker.recordClick(token, destination);
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(destination)).build();
    }

    @GetMapping(value = "/u/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribeShow(@PathVariable String token) {
        NewsletterDelivery delivery = deliveries.findByTrackingToken(token).orElse(null);
        return ResponseEntity.ok(unsubscribeHtml(token, delivery == null ? null : delivery.getEmailAtSend(), false));
    }

    @PostMapping(value = "/u/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribeStore(HttpServletRequest request, @PathVariable String token) {
        if (tooManyUnsubscribes(clientIp(request))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too Many Requests");
        }
        deliveries.findByTrackingToken(token).ifPresent(delivery -> contacts.findById(delivery.getContactId())
                .ifPresent(contact -> {
                    contact.setMarketingOptOutAt(Instant.now());
                    contacts.save(contact);
                }));
        NewsletterDelivery delivery = deliveries.findByTrackingToken(token).orElse(null);
        return ResponseEntity.ok(unsubscribeHtml(token, delivery == null ? null : delivery.getEmailAtSend(), true));
    }

    @GetMapping(value = "/v/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> view(@PathVariable String token) {
        NewsletterDelivery delivery = deliveries.findByTrackingToken(token).orElse(null);
        if (delivery == null) {
            return ResponseEntity.ok(
                    "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Email unavailable</title></head><body><p>This email is no longer available.</p></body></html>");
        }
        Newsletter newsletter = newsletters.findById(delivery.getNewsletterId()).orElse(null);
        Contact contact = contacts.findById(delivery.getContactId()).orElse(null);
        if (newsletter == null || contact == null) {
            return ResponseEntity.ok(
                    "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Email unavailable</title></head><body><p>This email is no longer available.</p></body></html>");
        }
        NewsletterTemplate template = newsletter.getTemplateId() == null
                ? null
                : templates.findById(newsletter.getTemplateId()).orElse(null);
        return ResponseEntity.ok(renderer.render(delivery, newsletter, contact, template));
    }

    private boolean tooManyUnsubscribes(String ip) {
        long now = System.currentTimeMillis();
        RateWindow window = UNSUBSCRIBE_ATTEMPTS.compute(ip, (key, existing) -> {
            if (existing == null || existing.expiresAt <= now) {
                return new RateWindow(1, now + 60_000);
            }
            existing.count++;
            return existing;
        });
        return window.count > 60;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String unsubscribeHtml(String token, String email, boolean confirmed) {
        String message = confirmed
                ? "You have been unsubscribed."
                : "Confirm that you want to unsubscribe from marketing emails.";
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Unsubscribe</title></head><body><main><h1>Unsubscribe</h1><p>"
                + escape(message)
                + "</p><p>"
                + escape(email == null ? "" : email)
                + "</p><form method=\"post\" action=\"/escalated/n/u/"
                + escape(token)
                + "\"><button type=\"submit\">Unsubscribe</button></form></main></body></html>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#039;");
    }

    private static final class RateWindow {
        private int count;
        private final long expiresAt;

        private RateWindow(int count, long expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }
    }
}
