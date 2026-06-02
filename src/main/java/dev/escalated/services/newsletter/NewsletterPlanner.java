package dev.escalated.services.newsletter;

import dev.escalated.models.Contact;
import dev.escalated.models.newsletter.Newsletter;
import dev.escalated.models.newsletter.NewsletterDelivery;
import dev.escalated.models.newsletter.NewsletterList;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.NewsletterDeliveryRepository;
import dev.escalated.repositories.NewsletterListRepository;
import dev.escalated.repositories.NewsletterRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class NewsletterPlanner {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ContactSegmentResolver segments;
    private final BounceSuppressionStore bounces;
    private final NewsletterRepository newsletters;
    private final NewsletterListRepository lists;
    private final NewsletterDeliveryRepository deliveries;
    private final ContactRepository contacts;

    public NewsletterPlanner(
            ContactSegmentResolver segments,
            BounceSuppressionStore bounces,
            NewsletterRepository newsletters,
            NewsletterListRepository lists,
            NewsletterDeliveryRepository deliveries,
            ContactRepository contacts) {
        this.segments = segments;
        this.bounces = bounces;
        this.newsletters = newsletters;
        this.lists = lists;
        this.deliveries = deliveries;
        this.contacts = contacts;
    }

    @Transactional
    public void plan(Newsletter newsletter) {
        newsletter.setStatus("sending");
        newsletters.save(newsletter);

        NewsletterList list = lists.findById(newsletter.getTargetListId()).orElse(null);
        if (list == null) {
            newsletter.setSummaryTotal(0);
            newsletters.save(newsletter);
            return;
        }

        List<Long> contactIds = segments.resolveSendable(list);
        if (contactIds.isEmpty()) {
            newsletter.setSummaryTotal(0);
            newsletters.save(newsletter);
            return;
        }

        List<Contact> contactRows = contacts.findAllById(contactIds);
        List<String> emails = contactRows.stream().map(Contact::getEmail).toList();
        Set<String> sendable = new HashSet<>();
        for (String email : bounces.filterSendable(emails)) {
            sendable.add(email.toLowerCase(Locale.ROOT));
        }

        List<NewsletterDelivery> rows = new ArrayList<>();
        for (Contact contact : contactRows) {
            if (!sendable.contains(contact.getEmail().toLowerCase(Locale.ROOT))) {
                continue;
            }
            NewsletterDelivery delivery = new NewsletterDelivery();
            delivery.setNewsletterId(newsletter.getId());
            delivery.setContactId(contact.getId());
            delivery.setEmailAtSend(contact.getEmail());
            delivery.setStatus("pending");
            delivery.setTrackingToken(token());
            delivery.setAttemptCount((short) 0);
            delivery.setTest(false);
            rows.add(delivery);
        }

        for (int i = 0; i < rows.size(); i += 500) {
            deliveries.saveAll(rows.subList(i, Math.min(i + 500, rows.size())));
        }
        newsletter.setSummaryTotal(rows.size());
        newsletters.save(newsletter);
    }

    private static String token() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(40);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
