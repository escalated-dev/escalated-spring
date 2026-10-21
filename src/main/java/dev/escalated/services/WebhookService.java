package dev.escalated.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.events.EventNames;
import dev.escalated.models.Webhook;
import dev.escalated.models.WebhookDelivery;
import dev.escalated.repositories.WebhookDeliveryRepository;
import dev.escalated.repositories.WebhookRepository;
import jakarta.persistence.EntityNotFoundException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
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
                // A redirect could point anywhere, including the private ranges
                // checked below. Never follow one.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** How a webhook host name is turned into addresses. Replaceable in tests. */
    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private HostResolver resolver = InetAddress::getAllByName;

    void setResolver(HostResolver resolver) {
        this.resolver = resolver;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Webhook> findAll() {
        return webhookRepository.findAll();
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Webhook findById(Long id) {
        return webhookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Webhook not found: " + id));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Webhook create(String url, String secret, String events, String description) {
        URI webhookUri = validateWebhookUrl(url);
        assertPublicHost(webhookUri, false);
        Webhook webhook = new Webhook();
        webhook.setUrl(webhookUri.toString());
        webhook.setSecret(secret);
        webhook.setEvents(EventNames.canonicalise(events));
        webhook.setDescription(description);
        webhook.setMaxRetries(properties.getWebhook().getMaxRetries());
        return webhookRepository.save(webhook);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Webhook update(Long id, String url, String events, String description, boolean active) {
        Webhook webhook = findById(id);
        URI webhookUri = validateWebhookUrl(url);
        assertPublicHost(webhookUri, false);
        webhook.setUrl(webhookUri.toString());
        webhook.setEvents(EventNames.canonicalise(events));
        webhook.setDescription(description);
        webhook.setActive(active);
        return webhookRepository.save(webhook);
    }

    /**
     * As {@link #update(Long, String, String, String, boolean)}, and replaces the signing secret
     * when {@code secret} is given; null or blank keeps the current one.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Webhook update(Long id, String url, String events, String description, boolean active, String secret) {
        Webhook webhook = update(id, url, events, description, active);
        if (secret != null && !secret.isBlank()) {
            webhook.setSecret(secret);
            webhook = webhookRepository.save(webhook);
        }
        return webhook;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void delete(Long id) {
        webhookRepository.deleteById(id);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void dispatchEvent(String eventName, Long entityId) {
        dispatchEvent(eventName, entityId, Map.of());
    }

    /**
     * Delivers {@code eventName} to every active webhook subscribed to it,
     * including webhooks whose stored subscription uses a legacy name for it
     * ({@code ticket.tagged} for {@code ticket.tag_added}, and so on).
     * {@code data} adds event-specific keys to the payload.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void dispatchEvent(String eventName, Long entityId, Map<String, Object> data) {
        String canonical = EventNames.canonical(eventName);
        List<Webhook> subscribed = new java.util.ArrayList<>();
        for (String name : EventNames.aliasesOf(canonical)) {
            for (Webhook webhook : webhookRepository.findActiveByEvent(name)) {
                // The query is a substring match; a subscription to
                // ticket.reopened must not receive ticket.opened.
                boolean seen = subscribed.stream().anyMatch(other -> other == webhook
                        || (other.getId() != null && other.getId().equals(webhook.getId())));
                if (!seen && EventNames.namesIn(webhook.getEvents()).contains(canonical)) {
                    subscribed.add(webhook);
                }
            }
        }
        for (Webhook webhook : subscribed) {
            deliverWebhook(webhook, canonical, entityId, data == null ? Map.of() : data);
        }
    }

    private void deliverWebhook(Webhook webhook, String eventName, Long entityId, Map<String, Object> data) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhook(webhook);
        delivery.setEventType(eventName);

        String payload;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("event", eventName);
            body.put("entity_id", entityId);
            body.put("timestamp", System.currentTimeMillis());
            data.forEach(body::putIfAbsent);
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            log.error("Webhook delivery failed for webhook {}: {}", webhook.getId(), ex.getMessage());
            delivery.setStatus("failed");
            delivery.setErrorMessage(ex.getMessage());
            delivery.setAttemptCount(1);
            webhook.setFailureCount(webhook.getFailureCount() + 1);
            webhookRepository.save(webhook);
            deliveryRepository.save(delivery);
            return;
        }
        delivery.setPayload(payload);
        send(webhook, delivery);
    }

    /**
     * Sends a recorded delivery's payload again, to its webhook's current URL and secret, as
     * Laravel's {@code WebhookDispatcher::retryDelivery}. The delivery records the new outcome and
     * one more attempt.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public WebhookDelivery retryDelivery(Long deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new EntityNotFoundException("Webhook delivery not found: " + deliveryId));
        delivery.setErrorMessage(null);
        delivery.setResponseStatus(null);
        delivery.setResponseBody(null);
        send(delivery.getWebhook(), delivery);
        return delivery;
    }

    /** Posts the delivery's payload and records the outcome on it and on the webhook. */
    private void send(Webhook webhook, WebhookDelivery delivery) {
        String eventName = delivery.getEventType();
        String payload = delivery.getPayload() == null ? "{}" : delivery.getPayload();
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        try {
            URI target = validateWebhookUrl(webhook.getUrl());
            // Checked at send time, every time: what a name resolves to can
            // change after the webhook was saved.
            assertPublicHost(target, true);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(target)
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
            webhook.setFailureCount(webhook.getFailureCount() + 1);
        }

        webhookRepository.save(webhook);
        deliveryRepository.save(delivery);
    }

    private static URI validateWebhookUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL is required");
        }

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Webhook URL is invalid", ex);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Webhook URL must include an absolute HTTP(S) host");
        }

        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Webhook URL must use HTTP or HTTPS");
        }

        if (isBlockedHost(host)) {
            throw new IllegalArgumentException("Webhook URL host is not allowed");
        }

        return uri;
    }

    /**
     * Resolves the host and refuses it when any address it resolves to is
     * private, loopback, link-local or otherwise not on the public internet,
     * so a DNS name pointing at {@code 127.0.0.1} or the cloud metadata
     * address is as blocked as the literal. At save time a name that does not
     * resolve yet is accepted ({@code strict=false}); at send time it is not.
     */
    private void assertPublicHost(URI uri, boolean strict) {
        String host = uri.getHost();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException ex) {
            if (strict) {
                throw new IllegalArgumentException("Webhook URL host does not resolve", ex);
            }
            return;
        }
        if (addresses == null || addresses.length == 0) {
            if (strict) {
                throw new IllegalArgumentException("Webhook URL host does not resolve");
            }
            return;
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new IllegalArgumentException("Webhook URL host is not allowed");
            }
        }
    }

    private static boolean isBlockedHost(String host) {
        String normalizedHost = host.toLowerCase();
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            return true;
        }

        if (isIpv4Literal(normalizedHost)) {
            return isBlockedAddress(parseIpv4Address(normalizedHost));
        }

        if (normalizedHost.matches("[0-9.]+")) {
            return true;
        }

        if (normalizedHost.contains(":")) {
            try {
                return isBlockedAddress(InetAddress.getByName(normalizedHost));
            } catch (Exception ex) {
                return true;
            }
        }

        return false;
    }

    private static boolean isIpv4Literal(String host) {
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static InetAddress parseIpv4Address(String host) {
        String[] parts = host.split("\\.");
        byte[] bytes = new byte[4];
        for (int i = 0; i < parts.length; i++) {
            int octet = Integer.parseInt(parts[i]);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Webhook URL host is invalid");
            }
            bytes[i] = (byte) octet;
        }

        try {
            return InetAddress.getByAddress(bytes);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Webhook URL host is invalid", ex);
        }
    }

    static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            int a0 = b[0] & 0xff;
            int a1 = b[1] & 0xff;
            return a0 == 0                                  // 0.0.0.0/8 "this network"
                    || a0 == 10 || a0 == 127                // private, loopback
                    || (a0 == 100 && (a1 & 0xc0) == 64)     // 100.64.0.0/10 carrier-grade NAT
                    || (a0 == 169 && a1 == 254)             // link-local, cloud metadata
                    || (a0 == 172 && (a1 & 0xf0) == 16)     // 172.16.0.0/12
                    || (a0 == 192 && a1 == 0 && (b[2] & 0xff) == 0) // 192.0.0.0/24 IETF
                    || (a0 == 192 && a1 == 168)             // 192.168.0.0/16
                    || (a0 == 198 && (a1 & 0xfe) == 18)     // 198.18.0.0/15 benchmarking
                    || a0 >= 224;                           // multicast, reserved, broadcast
        }
        int first = b[0] & 0xff;
        if ((first & 0xfe) == 0xfc) {
            return true;                                    // fc00::/7 unique local
        }
        if (first == 0xfe && (b[1] & 0xc0) == 0xc0) {
            return true;                                    // fec0::/10 old site-local
        }
        boolean zeroPrefix = true;
        for (int i = 0; i < 10; i++) {
            zeroPrefix &= b[i] == 0;
        }
        boolean mapped = zeroPrefix && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
        boolean compatible = zeroPrefix && b[10] == 0 && b[11] == 0;
        boolean nat64 = (b[0] & 0xff) == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff
                && (b[3] & 0xff) == 0x9b;
        if (mapped || compatible || nat64) {
            // An IPv4 address inside an IPv6 one reaches that IPv4 host.
            try {
                return isBlockedAddress(InetAddress.getByAddress(java.util.Arrays.copyOfRange(b, 12, 16)));
            } catch (UnknownHostException ex) {
                return true;
            }
        }
        return false;
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

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<WebhookDelivery> getDeliveries(Long webhookId) {
        return deliveryRepository.findByWebhookIdOrderByCreatedAtDesc(webhookId);
    }
}
