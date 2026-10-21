package dev.escalated.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Webhook;
import dev.escalated.models.WebhookDelivery;
import dev.escalated.repositories.WebhookDeliveryRepository;
import dev.escalated.repositories.WebhookRepository;
import jakarta.persistence.EntityNotFoundException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookRepository webhookRepository;
    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        EscalatedProperties props = new EscalatedProperties();
        webhookService = new WebhookService(webhookRepository, deliveryRepository, new ObjectMapper(), props);
    }

    @Test
    void create_shouldSaveWebhook() {
        when(webhookRepository.save(any(Webhook.class))).thenAnswer(inv -> {
            Webhook w = inv.getArgument(0);
            w.setId(1L);
            return w;
        });

        Webhook result = webhookService.create("https://example.com/hook", "secret123",
                "ticket.created,ticket.updated", "Test webhook");

        assertNotNull(result);
        assertEquals("https://example.com/hook", result.getUrl());
        assertEquals("secret123", result.getSecret());
    }

    @Test
    void create_shouldStoreLegacyEventNamesAsTheirCanonicalNames() {
        when(webhookRepository.save(any(Webhook.class))).thenAnswer(inv -> inv.getArgument(0));

        Webhook result = webhookService.create("https://example.com/hook", null,
                "ticket.created,ticket.tagged,internal_note.added,ticket.replied", "Legacy names");

        assertEquals("ticket.created,ticket.tag_added,note.created,reply.created", result.getEvents());
    }

    @Test
    void dispatchEvent_shouldDeliverToAWebhookSubscribedUnderALegacyName() {
        Webhook webhook = new Webhook();
        webhook.setId(4L);
        webhook.setUrl("https://hooks.example/x");
        webhook.setEvents("ticket.tagged");
        webhook.setActive(true);
        // Fails at send time, after the delivery row records the event name.
        webhookService.setResolver(host -> new InetAddress[] {InetAddress.getByName("127.0.0.1")});
        // Looked up under the canonical name and each legacy alias.
        org.mockito.Mockito.lenient().when(webhookRepository.findActiveByEvent(any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(webhookRepository.findActiveByEvent("ticket.tagged"))
                .thenReturn(List.of(webhook));
        ArgumentCaptor<WebhookDelivery> delivery = ArgumentCaptor.forClass(WebhookDelivery.class);

        webhookService.dispatchEvent("ticket.tag_added", 1L);

        verify(deliveryRepository).save(delivery.capture());
        assertEquals("ticket.tag_added", delivery.getValue().getEventType());
    }

    @Test
    void dispatchEvent_shouldNotDeliverToASubscriptionThatOnlyContainsTheNameAsASubstring() {
        Webhook webhook = new Webhook();
        webhook.setId(5L);
        webhook.setUrl("https://hooks.example/x");
        webhook.setEvents("ticket.status_changed_digest");
        webhook.setActive(true);
        when(webhookRepository.findActiveByEvent("ticket.status_changed")).thenReturn(List.of(webhook));

        webhookService.dispatchEvent("ticket.status_changed", 1L);

        verify(deliveryRepository, org.mockito.Mockito.never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/hook",
            "https://localhost/hook",
            "http://127.0.0.1/hook",
            "http://10.0.0.10/hook",
            "http://172.16.0.10/hook",
            "http://192.168.1.10/hook",
            "http://2130706433/hook",
            "http://169.254.169.254/latest/meta-data",
            "http://[::1]/hook"
    })
    void create_shouldRejectUnsafeWebhookUrls(String url) {
        assertThrows(IllegalArgumentException.class, () -> webhookService.create(url, null,
                "ticket.created", "Unsafe webhook"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "169.254.169.254", "10.1.2.3", "100.64.0.1", "0.0.0.0",
        "fd00::1", "fc00::1", "::ffff:192.168.0.1", "::1", "fe80::1"})
    void create_shouldRejectAHostNameThatResolvesToAPrivateAddress(String address) {
        webhookService.setResolver(host -> new InetAddress[] {InetAddress.getByName(address)});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> webhookService.create(
                "https://metadata.attacker.example/hook", null, "ticket.created", "Rebinding"));
        assertEquals("Webhook URL host is not allowed", ex.getMessage());
    }

    @Test
    void create_shouldRejectAHostWithAnyPrivateAddressAmongItsRecords() {
        webhookService.setResolver(host -> new InetAddress[] {
            InetAddress.getByName("93.184.216.34"), InetAddress.getByName("127.0.0.1")});

        assertThrows(IllegalArgumentException.class, () -> webhookService.create(
                "https://mixed.attacker.example/hook", null, "ticket.created", "Mixed"));
    }

    @Test
    void dispatchEvent_shouldNotSendToAHostThatNowResolvesToAPrivateAddress() {
        // Saved while it resolved publicly; DNS now points it at the metadata service.
        Webhook webhook = new Webhook();
        webhook.setId(2L);
        webhook.setUrl("https://hooks.attacker.example/x");
        webhook.setEvents("ticket.created");
        webhook.setActive(true);
        webhookService.setResolver(host -> new InetAddress[] {InetAddress.getByName("169.254.169.254")});

        when(webhookRepository.findActiveByEvent("ticket.created")).thenReturn(List.of(webhook));
        ArgumentCaptor<WebhookDelivery> delivery = ArgumentCaptor.forClass(WebhookDelivery.class);

        webhookService.dispatchEvent("ticket.created", 1L);

        verify(deliveryRepository).save(delivery.capture());
        assertEquals("failed", delivery.getValue().getStatus());
        assertEquals("Webhook URL host is not allowed", delivery.getValue().getErrorMessage());
        assertNull(delivery.getValue().getResponseStatus());
    }

    @Test
    void dispatchEvent_shouldNotSendToAHostThatNoLongerResolves() {
        Webhook webhook = new Webhook();
        webhook.setId(3L);
        webhook.setUrl("https://gone.attacker.example/x");
        webhook.setEvents("ticket.created");
        webhook.setActive(true);
        webhookService.setResolver(host -> {
            throw new UnknownHostException(host);
        });

        when(webhookRepository.findActiveByEvent("ticket.created")).thenReturn(List.of(webhook));
        ArgumentCaptor<WebhookDelivery> delivery = ArgumentCaptor.forClass(WebhookDelivery.class);

        webhookService.dispatchEvent("ticket.created", 1L);

        verify(deliveryRepository).save(delivery.capture());
        assertEquals("failed", delivery.getValue().getStatus());
        assertNull(delivery.getValue().getResponseStatus());
    }

    @Test
    void findById_shouldThrowWhenNotFound() {
        when(webhookRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> webhookService.findById(999L));
    }

    @Test
    void dispatchEvent_shouldDeliverToMatchingWebhooks() {
        Webhook webhook = new Webhook();
        webhook.setId(1L);
        webhook.setUrl("https://example.com/hook");
        webhook.setEvents("ticket.created");
        webhook.setActive(true);

        when(webhookRepository.findActiveByEvent("ticket.created")).thenReturn(List.of(webhook));
        when(webhookRepository.save(any())).thenReturn(webhook);
        when(deliveryRepository.save(any())).thenReturn(null);

        webhookService.dispatchEvent("ticket.created", 1L);

        verify(deliveryRepository).save(any());
    }

    @Test
    void delete_shouldDeleteWebhook() {
        webhookService.delete(1L);

        verify(webhookRepository).deleteById(1L);
    }
}
