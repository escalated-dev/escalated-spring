package dev.escalated.services.newsletter;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Contact;
import dev.escalated.models.newsletter.Newsletter;
import dev.escalated.models.newsletter.NewsletterDelivery;
import dev.escalated.models.newsletter.NewsletterTemplate;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.NewsletterDeliveryRepository;
import dev.escalated.repositories.NewsletterRepository;
import dev.escalated.repositories.NewsletterTemplateRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class NewsletterDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NewsletterDispatcher.class);
    private static final int[] BACKOFF_MINUTES = {1, 5, 30};

    private final EscalatedProperties properties;
    private final NewsletterRepository newsletters;
    private final NewsletterDeliveryRepository deliveries;
    private final NewsletterTemplateRepository templates;
    private final ContactRepository contacts;
    private final NewsletterRenderer renderer;
    private final JavaMailSender mailSender;
    private final Map<String, MinuteCounter> sentByMinute = new ConcurrentHashMap<>();

    public NewsletterDispatcher(
            EscalatedProperties properties,
            NewsletterRepository newsletters,
            NewsletterDeliveryRepository deliveries,
            NewsletterTemplateRepository templates,
            ContactRepository contacts,
            NewsletterRenderer renderer,
            @Autowired(required = false) JavaMailSender mailSender) {
        this.properties = properties;
        this.newsletters = newsletters;
        this.deliveries = deliveries;
        this.templates = templates;
        this.contacts = contacts;
        this.renderer = renderer;
        this.mailSender = mailSender;
    }

    @Transactional
    public void dispatchBatch() {
        if (!properties.getNewsletters().isEnabled()) {
            return;
        }

        reclaimStuckRows();

        EscalatedProperties.NewslettersProperties config = properties.getNewsletters();
        int batchSize = config.getBatchSize();
        int rateLimit = config.getRateLimitPerMinute();
        int allowance = Math.max(0, rateLimit - sentThisMinute());

        if (allowance == 0) {
            finalizeCompletedNewsletters();
            checkAutoPauseAcrossActiveNewsletters();
            return;
        }

        Instant now = Instant.now();
        List<NewsletterDelivery> pending = deliveries.findPendingForDispatch(
                now, PageRequest.of(0, Math.min(batchSize, allowance)));

        if (pending.isEmpty()) {
            finalizeCompletedNewsletters();
            checkAutoPauseAcrossActiveNewsletters();
            return;
        }

        Instant claimedAt = Instant.now();
        for (NewsletterDelivery delivery : pending) {
            delivery.setStatus("queued");
            delivery.setClaimedAt(claimedAt);
        }
        deliveries.saveAll(pending);
        incrementSentThisMinute(pending.size());

        for (NewsletterDelivery delivery : pending) {
            dispatchOne(delivery.getId());
        }

        finalizeCompletedNewsletters();
        checkAutoPauseAcrossActiveNewsletters();
    }

    private void dispatchOne(Long deliveryId) {
        NewsletterDelivery delivery = deliveries.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }
        Newsletter newsletter = newsletters.findById(delivery.getNewsletterId()).orElse(null);
        if (newsletter == null) {
            return;
        }
        Contact contact = contacts.findById(delivery.getContactId()).orElse(null);
        if (contact == null) {
            return;
        }
        NewsletterTemplate template = newsletter.getTemplateId() == null
                ? null
                : templates.findById(newsletter.getTemplateId()).orElse(null);

        try {
            if (mailSender == null) {
                throw new IllegalStateException("Mailer not configured — set spring.mail.* to enable sending");
            }
            String html = renderer.render(delivery, newsletter, contact, template);
            String unsub = renderer.unsubscribeUrl(delivery);
            String host = hostFromAppUrl();

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(delivery.getEmailAtSend());
            helper.setFrom(formatFrom(newsletter));
            if (newsletter.getReplyTo() != null && !newsletter.getReplyTo().isBlank()) {
                helper.setReplyTo(newsletter.getReplyTo());
            }
            helper.setSubject(newsletter.getSubject());
            helper.setText(html, true);
            message.setHeader("List-Unsubscribe", "<" + unsub + ">");
            message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            message.setHeader("X-Escalated-Newsletter-Id", String.valueOf(newsletter.getId()));
            message.setHeader(
                    "Message-ID",
                    "<n-" + newsletter.getId() + "-" + delivery.getTrackingToken() + "@" + host + ">");
            mailSender.send(message);

            delivery.setStatus("sent");
            delivery.setSentAt(Instant.now());
            delivery.setClaimedAt(null);
            delivery.setNextAttemptAt(null);
            deliveries.save(delivery);
            newsletter.incrementSummarySent();
            newsletters.save(newsletter);
        } catch (Exception ex) {
            log.warn("Newsletter delivery {} failed: {}", delivery.getId(), ex.getMessage());
            short attempts = (short) (delivery.getAttemptCount() + 1);
            delivery.setAttemptCount(attempts);
            delivery.setClaimedAt(null);
            if (attempts >= 3) {
                delivery.setStatus("failed");
                delivery.setFailureReason(ex.getMessage());
                delivery.setNextAttemptAt(null);
            } else {
                delivery.setStatus("pending");
                delivery.setNextAttemptAt(backoffDate(attempts));
            }
            deliveries.save(delivery);
        }
    }

    private String formatFrom(Newsletter newsletter) throws Exception {
        if (newsletter.getFromName() != null && !newsletter.getFromName().isBlank()) {
            return new InternetAddress(newsletter.getFromEmail(), newsletter.getFromName()).toString();
        }
        return newsletter.getFromEmail();
    }

    private void reclaimStuckRows() {
        int minutes = properties.getNewsletters().getClaimTimeoutMinutes();
        Instant cutoff = Instant.now().minusSeconds(minutes * 60L);
        deliveries.reclaimStuck(cutoff);
    }

    private void finalizeCompletedNewsletters() {
        for (Newsletter newsletter : newsletters.findByStatus("sending")) {
            long remaining = deliveries.countByNewsletterIdAndStatusIn(
                    newsletter.getId(), List.of("pending", "queued"));
            if (remaining == 0) {
                newsletter.setStatus("sent");
                if (newsletter.getSentAt() == null) {
                    newsletter.setSentAt(Instant.now());
                }
                newsletters.save(newsletter);
            }
        }
    }

    private void checkAutoPauseAcrossActiveNewsletters() {
        int threshold = properties.getNewsletters().getAutoPauseThreshold();
        double rate = properties.getNewsletters().getAutoPauseBounceRate();
        for (Newsletter newsletter : newsletters.findByStatus("sending")) {
            List<NewsletterDelivery> sample = deliveries.findFirstTerminalByNewsletter(
                    newsletter.getId(), PageRequest.of(0, threshold));
            if (sample.size() < threshold) {
                continue;
            }
            long bounced = sample.stream().filter(d -> "bounced".equals(d.getStatus())).count();
            if (bounced / (double) threshold >= rate) {
                newsletter.setStatus("paused");
                newsletters.save(newsletter);
                log.warn("Newsletter {} auto-paused: {}/{} bounced", newsletter.getId(), bounced, threshold);
            }
        }
    }

    private Instant backoffDate(short attempts) {
        int index = Math.max(0, attempts - 1);
        int minutes = BACKOFF_MINUTES[Math.min(index, BACKOFF_MINUTES.length - 1)];
        return Instant.now().plusSeconds(minutes * 60L);
    }

    private int sentThisMinute() {
        MinuteCounter counter = sentByMinute.get(minuteKey());
        if (counter == null || counter.expiresAt <= System.currentTimeMillis()) {
            return 0;
        }
        return counter.count;
    }

    private void incrementSentThisMinute(int count) {
        String key = minuteKey();
        sentByMinute.compute(key, (k, existing) -> {
            int current = existing == null || existing.expiresAt <= System.currentTimeMillis()
                    ? 0
                    : existing.count;
            return new MinuteCounter(current + count, System.currentTimeMillis() + 120_000);
        });
    }

    private static String minuteKey() {
        return DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC).format(Instant.now());
    }

    private String hostFromAppUrl() {
        try {
            return new java.net.URI(properties.getNewsletters().getAppUrl()).getHost();
        } catch (Exception ex) {
            return "localhost";
        }
    }

    private record MinuteCounter(int count, long expiresAt) {}
}
