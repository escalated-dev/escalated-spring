package dev.escalated.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Webhook;
import dev.escalated.repositories.WebhookDeliveryRepository;
import dev.escalated.repositories.WebhookRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
