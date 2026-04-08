package dev.escalated.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Webhook;
import dev.escalated.models.WebhookDelivery;
import dev.escalated.repositories.WebhookDeliveryRepository;
import dev.escalated.repositories.WebhookRepository;
import jakarta.persistence.EntityNotFoundException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final EscalatedProperties properties;
    private final HttpClient httpClient;

    public WebhookService(WebhookRepository webhookRepository,
                          WebhookDeliveryRepository deliveryRepository,
                          ObjectMapper objectMapper,
                          EscalatedProperties properties) {
        this.webhookRepository = webhookRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Transactional(readOnly = true)
    public List<Webhook> findAll() {
        return webhookRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Webhook findById(Long id) {
        return webhookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Webhook not found: " + id));
    }

    @Transactional
    public Webhook create(String url, String secret, String events, String description) {
        Webhook webhook = new Webhook();
        webhook.setUrl(url);
        webhook.setSecret(secret);
        webhook.setEvents(events);
        webhook.setDescription(description);
        webhook.setMaxRetries(properties.getWebhook().getMaxRetries());
        return webhookRepository.save(webhook);
    }

    @Transactional
    public Webhook update(Long id, String url, String events, String description, boolean active) {
        Webhook webhook = findById(id);
        webhook.setUrl(url);
        webhook.setEvents(events);
        webhook.setDescription(description);
        webhook.setActive(active);
        return webhookRepository.save(webhook);
    }

    @Transactional
    public void delete(Long id) {
        webhookRepository.deleteById(id);
    }

    @Transactional
    public void dispatchEvent(String eventName, Long entityId) {
        List<Webhook> webhooks = webhookRepository.findActiveByEvent(eventName);
        for (Webhook webhook : webhooks) {
            deliverWebhook(webhook, eventName, entityId);
        }
    }

    private void deliverWebhook(Webhook webhook, String eventName, Long entityId) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhook(webhook);
        delivery.setEventType(eventName);

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "event", eventName,
                    "entity_id", entityId,
                    "timestamp", System.currentTimeMillis()
            ));
            delivery.setPayload(payload);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-Escalated-Event", eventName)
                    .POST(HttpRequest.BodyPublishers.ofString(payload));

            if (webhook.getSecret() != null && !webhook.getSecret().isEmpty()) {
                String signature = computeHmac(payload, webhook.getSecret());
                requestBuilder.header("X-Escalated-Signature", signature);
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            delivery.setResponseStatus(response.statusCode());
            delivery.setResponseBody(response.body());
            delivery.setAttemptCount(1);

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus("success");
                webhook.setFailureCount(0);
            } else {
                delivery.setStatus("failed");
                webhook.setFailureCount(webhook.getFailureCount() + 1);
            }
        } catch (Exception ex) {
            log.error("Webhook delivery failed for webhook {}: {}", webhook.getId(), ex.getMessage());
            delivery.setStatus("failed");
            delivery.setErrorMessage(ex.getMessage());
            delivery.setAttemptCount(1);
            webhook.setFailureCount(webhook.getFailureCount() + 1);
        }

        webhookRepository.save(webhook);
        deliveryRepository.save(delivery);
    }

    private String computeHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return "sha256=" + hexString;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to compute HMAC", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> getDeliveries(Long webhookId) {
        return deliveryRepository.findByWebhookIdOrderByCreatedAtDesc(webhookId);
    }
}
