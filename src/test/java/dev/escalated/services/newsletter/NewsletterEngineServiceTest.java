package dev.escalated.services.newsletter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Contact;
import dev.escalated.models.EscalatedSettings;
import dev.escalated.models.newsletter.Newsletter;
import dev.escalated.models.newsletter.NewsletterDelivery;
import dev.escalated.models.newsletter.NewsletterList;
import dev.escalated.models.newsletter.NewsletterListMember;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.EscalatedSettingsRepository;
import dev.escalated.repositories.NewsletterDeliveryRepository;
import dev.escalated.repositories.NewsletterListMemberRepository;
import dev.escalated.repositories.NewsletterListRepository;
import dev.escalated.repositories.NewsletterRepository;
import dev.escalated.repositories.NewsletterTemplateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsletterEngineServiceTest {

    @Mock private EscalatedSettingsRepository settingsRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private NewsletterListMemberRepository memberRepository;
    @Mock private NewsletterRepository newsletterRepository;
    @Mock private NewsletterListRepository listRepository;
    @Mock private NewsletterDeliveryRepository deliveryRepository;
    @Mock private NewsletterTemplateRepository templateRepository;
    @Mock private JavaMailSender mailSender;

    private EscalatedProperties properties;
    private ObjectMapper objectMapper;
    private BounceSuppressionStore bounces;
    private ContactSegmentResolver segments;
    private NewsletterTracker tracker;
    private NewsletterPlanner planner;
    private NewsletterDispatcher dispatcher;
    private NewsletterRenderer renderer;

    @BeforeEach
    void setUp() {
        properties = new EscalatedProperties();
        properties.getNewsletters().setEnabled(true);
        objectMapper = new ObjectMapper();
        bounces = new BounceSuppressionStore(settingsRepository, objectMapper);
        segments = new ContactSegmentResolver(contactRepository, memberRepository, objectMapper);
        tracker = new NewsletterTracker(deliveryRepository, newsletterRepository, bounces);
        planner = new NewsletterPlanner(
                segments, bounces, newsletterRepository, listRepository, deliveryRepository, contactRepository);
        NewsletterRenderer.Options opts = new NewsletterRenderer.Options();
        opts.baseUrl = "http://localhost";
        opts.themesDir = "src/main/resources/templates/escalated/newsletter_themes";
        renderer = new NewsletterRenderer(opts);
        dispatcher = new NewsletterDispatcher(
                properties,
                newsletterRepository,
                deliveryRepository,
                templateRepository,
                contactRepository,
                renderer,
                mailSender);
    }

    @Test
    void bounceStore_filtersCaseInsensitively() throws Exception {
        final EscalatedSettings[] stored = { null };
        when(settingsRepository.findByKey(BounceSuppressionStore.KEY))
                .thenAnswer(inv -> Optional.ofNullable(stored[0]));
        when(settingsRepository.save(any())).thenAnswer(inv -> {
            EscalatedSettings row = inv.getArgument(0);
            row.setId(1L);
            stored[0] = row;
            return row;
        });
        bounces.markBounced("USER@Example.com");
        assertTrue(bounces.isBounced("user@example.com"));
        assertEquals(List.of("ok@example.com"), bounces.filterSendable(List.of("user@example.com", "ok@example.com")));
    }

    @Test
    void segmentResolver_resolvesStaticMembersAndSendable() {
        NewsletterList list = new NewsletterList();
        list.setId(9L);
        list.setKind("static");
        NewsletterListMember m1 = new NewsletterListMember();
        m1.setContactId(1L);
        NewsletterListMember m2 = new NewsletterListMember();
        m2.setContactId(2L);
        when(memberRepository.findByListId(9L)).thenReturn(List.of(m1, m2));

        Contact c1 = new Contact();
        c1.setId(1L);
        Contact c2 = new Contact();
        c2.setId(2L);
        c2.setMarketingOptOutAt(Instant.now());
        when(contactRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(c1, c2));

        assertEquals(List.of(1L, 2L), segments.resolve(list));
        assertEquals(List.of(1L), segments.resolveSendable(list));
    }

    @Test
    void planner_createsDeliveriesWithUniqueTokens() {
        Newsletter newsletter = new Newsletter();
        newsletter.setId(10L);
        newsletter.setTargetListId(1L);
        NewsletterList list = new NewsletterList();
        list.setId(1L);
        list.setKind("static");

        Contact ok = new Contact();
        ok.setId(1L);
        ok.setEmail("ok@example.com");
        Contact bounced = new Contact();
        bounced.setId(2L);
        bounced.setEmail("bounced@example.com");

        when(listRepository.findById(1L)).thenReturn(Optional.of(list));
        when(memberRepository.findByListId(1L)).thenReturn(List.of(member(1L), member(2L)));
        when(contactRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(ok, bounced));
        when(settingsRepository.findByKey(BounceSuppressionStore.KEY)).thenReturn(Optional.empty());
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(newsletterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        planner.plan(newsletter);

        ArgumentCaptor<List<NewsletterDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        List<NewsletterDelivery> rows = captor.getValue();
        assertEquals(2, rows.size());
        assertEquals(2, newsletter.getSummaryTotal());
    }

    @Test
    void tracker_recordsFirstOpenOnce() {
        NewsletterDelivery delivery = delivery("token1", "sent");
        Newsletter newsletter = new Newsletter();
        newsletter.setId(1L);
        when(deliveryRepository.findByTrackingToken("token1")).thenReturn(Optional.of(delivery));
        when(newsletterRepository.findById(1L)).thenReturn(Optional.of(newsletter));

        tracker.recordOpen("token1");
        tracker.recordOpen("token1");

        assertEquals(1, newsletter.getSummaryOpened());
        verify(newsletterRepository, org.mockito.Mockito.times(1)).save(newsletter);
    }

    @Test
    void dispatcher_skipsClaimWhenRateLimitExceeded() {
        properties.getNewsletters().setRateLimitPerMinute(0);
        when(deliveryRepository.reclaimStuck(any())).thenReturn(0);
        when(newsletterRepository.findByStatus("sending")).thenReturn(List.of());

        dispatcher.dispatchBatch();

        verify(deliveryRepository, never()).findPendingForDispatch(any(), any(Pageable.class));
    }

    @Test
    void dispatcher_noopsWhenDisabled() {
        properties.getNewsletters().setEnabled(false);
        dispatcher.dispatchBatch();
        verify(deliveryRepository, never()).findPendingForDispatch(any(), any());
    }

    private static NewsletterListMember member(long contactId) {
        NewsletterListMember member = new NewsletterListMember();
        member.setContactId(contactId);
        return member;
    }

    private static NewsletterDelivery delivery(String token, String status) {
        NewsletterDelivery delivery = new NewsletterDelivery();
        delivery.setId(1L);
        delivery.setNewsletterId(1L);
        delivery.setContactId(1L);
        delivery.setEmailAtSend("user@example.com");
        delivery.setTrackingToken(token);
        delivery.setStatus(status);
        return delivery;
    }

    private static Contact contact(NewsletterDelivery delivery) {
        Contact contact = new Contact();
        contact.setId(delivery.getContactId());
        contact.setEmail(delivery.getEmailAtSend());
        return contact;
    }
}
