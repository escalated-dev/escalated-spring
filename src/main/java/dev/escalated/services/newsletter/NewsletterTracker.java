package dev.escalated.services.newsletter;

import dev.escalated.models.newsletter.NewsletterDelivery;
import dev.escalated.repositories.NewsletterDeliveryRepository;
import dev.escalated.repositories.NewsletterRepository;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class NewsletterTracker {

    private static final Set<String> TERMINAL = Set.of("bounced", "complained", "failed");

    private final NewsletterDeliveryRepository deliveries;
    private final NewsletterRepository newsletters;
    private final BounceSuppressionStore bounces;

    public NewsletterTracker(
            NewsletterDeliveryRepository deliveries,
            NewsletterRepository newsletters,
            BounceSuppressionStore bounces) {
        this.deliveries = deliveries;
        this.newsletters = newsletters;
        this.bounces = bounces;
    }

    @Transactional
    public void recordOpen(String token) {
        NewsletterDelivery delivery = findByToken(token);
        if (delivery == null || TERMINAL.contains(delivery.getStatus()) || delivery.getOpenedAt() != null) {
            return;
        }
        delivery.setOpenedAt(Instant.now());
        deliveries.save(delivery);
        newsletters.findById(delivery.getNewsletterId()).ifPresent(n -> {
            n.incrementSummaryOpened();
            newsletters.save(n);
        });
    }

    @Transactional
    public void recordClick(String token, String url) {
        NewsletterDelivery delivery = findByToken(token);
        if (delivery == null || TERMINAL.contains(delivery.getStatus())) {
            return;
        }
        boolean firstClick = delivery.getClicksCount() == 0;
        delivery.setClicksCount(delivery.getClicksCount() + 1);
        delivery.setLastClickedAt(Instant.now());
        if (delivery.getOpenedAt() == null) {
            delivery.setOpenedAt(Instant.now());
            newsletters.findById(delivery.getNewsletterId()).ifPresent(n -> {
                n.incrementSummaryOpened();
                newsletters.save(n);
            });
        }
        deliveries.save(delivery);
        if (firstClick) {
            newsletters.findById(delivery.getNewsletterId()).ifPresent(n -> {
                n.incrementSummaryClicked();
                newsletters.save(n);
            });
        }
    }

    @Transactional
    public void recordBounce(String token, String type, String reason) {
        if (!"hard".equals(type)) {
            return;
        }
        NewsletterDelivery delivery = findByToken(token);
        if (delivery == null || "bounced".equals(delivery.getStatus())) {
            return;
        }
        delivery.setStatus("bounced");
        delivery.setBounceReason(reason);
        deliveries.save(delivery);
        newsletters.findById(delivery.getNewsletterId()).ifPresent(n -> {
            n.incrementSummaryBounced();
            newsletters.save(n);
        });
        bounces.markBounced(delivery.getEmailAtSend());
    }

    @Transactional
    public void recordComplaint(String token) {
        NewsletterDelivery delivery = findByToken(token);
        if (delivery == null || "complained".equals(delivery.getStatus())) {
            return;
        }
        delivery.setStatus("complained");
        deliveries.save(delivery);
        newsletters.findById(delivery.getNewsletterId()).ifPresent(n -> {
            n.incrementSummaryComplained();
            newsletters.save(n);
        });
        bounces.markComplained(delivery.getEmailAtSend());
    }

    private NewsletterDelivery findByToken(String token) {
        return deliveries.findByTrackingToken(token).orElse(null);
    }
}
