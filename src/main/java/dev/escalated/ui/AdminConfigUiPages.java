package dev.escalated.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.events.EventNames;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.CannedResponse;
import dev.escalated.models.Macro;
import dev.escalated.models.Webhook;
import dev.escalated.models.WebhookDelivery;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.CannedResponseRepository;
import dev.escalated.repositories.MacroRepository;
import dev.escalated.repositories.WebhookDeliveryRepository;
import dev.escalated.repositories.WebhookRepository;
import dev.escalated.services.MacroService;
import dev.escalated.services.WebhookService;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin configuration pages' props and writes: macros, canned responses and webhooks, as
 * escalated-laravel's {@code Admin\MacroController}, {@code CannedResponseController} and
 * {@code WebhookController}. Built field by field: a webhook's signing secret never reaches a page.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class AdminConfigUiPages {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DELIVERIES_PER_PAGE = 25;
    private static final String PUBLIC_URL = "The webhook URL must use http or https and resolve to a public address.";

    private final MacroRepository macros;
    private final CannedResponseRepository cannedResponses;
    private final AgentProfileRepository agents;
    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookService webhookService;
    private final UiRoutes routes;

    public AdminConfigUiPages(MacroRepository macros, CannedResponseRepository cannedResponses,
                              AgentProfileRepository agents, WebhookRepository webhooks,
                              WebhookDeliveryRepository deliveries, WebhookService webhookService, UiRoutes routes) {
        this.macros = macros;
        this.cannedResponses = cannedResponses;
        this.agents = agents;
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.webhookService = webhookService;
        this.routes = routes;
    }

    // ── Macros ──────────────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> macros() {
        List<Macro> all = new ArrayList<>(macros.findAll());
        all.sort(Comparator.comparingInt(Macro::getSortOrder).thenComparing(Macro::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Macro macro : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", macro.getId());
            row.put("name", macro.getName());
            row.put("description", macro.getDescription());
            row.put("actions", actions(macro.getActions()));
            row.put("is_shared", macro.isShared());
            row.put("is_active", macro.isActive());
            row.put("order", macro.getSortOrder());
            row.put("created_by", macro.getCreatedByAgentId());
            row.put("created_at", UiPresenter.iso(macro.getCreatedAt()));
            rows.add(row);
        }
        return Map.of("macros", rows);
    }

    /** Validates as Laravel's {@code StoreMacroRequest} and saves. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void saveMacro(Long id, UiInput input, Long agentId) {
        String name = input.requiredString("name", 255);
        String description = input.string("description");
        if (description != null && description.length() > 500) {
            throw UiInput.invalid("description", "The description field must not be greater than 500 characters.");
        }
        List<Object> given = input.list("actions");
        if (given == null || given.isEmpty()) {
            throw UiInput.invalid("actions", "The actions field is required.");
        }
        List<Map<String, Object>> actions = new ArrayList<>();
        for (int index = 0; index < given.size(); index++) {
            if (!(given.get(index) instanceof Map<?, ?> action)) {
                throw UiInput.invalid("actions." + index, "The actions." + index + " field must be an array.");
            }
            Object type = action.get("type");
            if (type == null || !MacroService.PAGE_ACTION_TYPES.contains(String.valueOf(type))) {
                throw UiInput.invalid("actions." + index + ".type", "The selected actions." + index + ".type is invalid.");
            }
            Object value = action.get("value");
            if (value == null || String.valueOf(value).isBlank()) {
                throw UiInput.invalid("actions." + index + ".value", "The actions." + index + ".value field is required.");
            }
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("type", String.valueOf(type));
            stored.put("value", value instanceof List<?> list ? String.join(",", list.stream().map(String::valueOf).toList())
                    : String.valueOf(value));
            stored.put("order", index);
            actions.add(stored);
        }
        Long order = input.longValue("order");
        if (order != null && order < 0) {
            throw UiInput.invalid("order", "The order field must be at least 0.");
        }

        Macro macro = id == null ? new Macro() : findMacro(id);
        macro.setName(name);
        macro.setDescription(description);
        macro.setActions(write(actions));
        macro.setShared(input.bool("is_shared", id == null ? false : macro.isShared()));
        if (order != null) {
            macro.setSortOrder(order.intValue());
        }
        if (id == null) {
            macro.setCreatedByAgentId(agentId);
        }
        macros.save(macro);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteMacro(Long id) {
        macros.delete(findMacro(id));
    }

    // ── Canned responses ────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> cannedResponses() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CannedResponse response : cannedResponses.findAll(Sort.by("id"))) {
            Map<String, Object> row = UiPresenter.cannedResponse(response);
            row.put("created_by", response.getCreatedByAgentId());
            AgentProfile creator = response.getCreatedByAgentId() == null ? null
                    : agents.findById(response.getCreatedByAgentId()).orElse(null);
            row.put("creator", creator == null ? null : UiPresenter.agentName(creator));
            row.put("created_at", UiPresenter.iso(response.getCreatedAt()));
            rows.add(row);
        }
        return Map.of("responses", rows);
    }

    /** Validates as Laravel's {@code StoreCannedResponseRequest} and saves. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void saveCannedResponse(Long id, UiInput input, Long agentId) {
        String title = input.requiredString("title", 255);
        String body = input.requiredString("body", 65535);
        String category = input.string("category");
        if (category != null && category.length() > 100) {
            throw UiInput.invalid("category", "The category field must not be greater than 100 characters.");
        }
        CannedResponse response = id == null ? new CannedResponse() : findCannedResponse(id);
        response.setTitle(title);
        response.setContent(body);
        response.setCategory(category);
        response.setShared(input.bool("is_shared", id == null || response.isShared()));
        if (id == null) {
            response.setCreatedByAgentId(agentId);
        }
        cannedResponses.save(response);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteCannedResponse(Long id) {
        cannedResponses.delete(findCannedResponse(id));
    }

    // ── Webhooks ────────────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> webhooks() {
        List<Webhook> all = new ArrayList<>(webhooks.findAll());
        all.sort(Comparator.comparing(Webhook::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Webhook::getId, Comparator.reverseOrder()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Webhook webhook : all) {
            Map<String, Object> row = webhook(webhook);
            List<Map<String, Object>> latest = new ArrayList<>();
            deliveries.findFirstByWebhookIdOrderByCreatedAtDescIdDesc(webhook.getId())
                    .ifPresent(delivery -> latest.add(delivery(delivery)));
            row.put("deliveries", latest);
            rows.add(row);
        }
        return Map.of("webhooks", rows);
    }

    /** The form's props. A stored secret is never sent back; the form leaves it blank to keep it. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> webhookForm(Long id) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (id != null) {
            props.put("webhook", webhook(findWebhook(id)));
        }
        props.put("availableEvents", EventNames.EMITTED);
        return props;
    }

    /** Validates as Laravel's {@code WebhookController} and saves through {@link WebhookService}. */
    public void saveWebhook(Long id, UiInput input) {
        String url = input.requiredString("url", 500);
        List<Object> given = input.list("events");
        if (given == null || given.isEmpty()) {
            throw UiInput.invalid("events", "The events field is required.");
        }
        Set<String> events = new LinkedHashSet<>();
        for (Object event : given) {
            if (event != null && !String.valueOf(event).isBlank()) {
                events.add(EventNames.canonical(String.valueOf(event)));
            }
        }
        if (events.isEmpty()) {
            throw UiInput.invalid("events", "The events field is required.");
        }
        String secret = input.string("secret");
        if (secret != null && secret.length() > 255) {
            throw UiInput.invalid("secret", "The secret field must not be greater than 255 characters.");
        }
        boolean active = input.bool("active", true);
        try {
            if (id == null) {
                Webhook created = webhookService.create(url, secret, String.join(",", events), null);
                if (!active) {
                    webhookService.update(created.getId(), url, String.join(",", events), null, false);
                }
            } else {
                Webhook existing = findWebhook(id);
                webhookService.update(id, url, String.join(",", events), existing.getDescription(), active, secret);
            }
        } catch (IllegalArgumentException refused) {
            throw UiInput.invalid("url", PUBLIC_URL);
        }
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteWebhook(Long id) {
        findWebhook(id);
        webhookService.delete(id);
    }

    /** {@code webhook} and its deliveries, newest first, as Laravel's 25-a-page paginator. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> deliveries(Long webhookId, int page) {
        Webhook webhook = findWebhook(webhookId);
        Page<WebhookDelivery> found = deliveries.findByWebhookId(webhookId, PageRequest.of(Math.max(page, 1) - 1,
                DELIVERIES_PER_PAGE, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WebhookDelivery delivery : found) {
            rows.add(delivery(delivery));
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("webhook", webhook(webhook));
        props.put("deliveries", UiPresenter.paginator(found, rows,
                routes.url("escalated.admin.webhooks.deliveries", webhookId)));
        return props;
    }

    /** The webhook a delivery belongs to, or a 404. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public void assertDelivery(Long deliveryId) {
        deliveries.findById(deliveryId).orElseThrow(() -> new EntityNotFoundException("Delivery not found"));
    }

    private static Map<String, Object> webhook(Webhook webhook) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", webhook.getId());
        map.put("url", webhook.getUrl());
        // Shown under its current name, so the form ticks a legacy subscription and saving keeps it.
        map.put("events", EventNames.namesIn(webhook.getEvents()));
        map.put("active", webhook.isActive());
        map.put("description", webhook.getDescription());
        map.put("has_secret", webhook.getSecret() != null && !webhook.getSecret().isEmpty());
        map.put("failure_count", webhook.getFailureCount());
        map.put("created_at", UiPresenter.iso(webhook.getCreatedAt()));
        map.put("updated_at", UiPresenter.iso(webhook.getUpdatedAt()));
        return map;
    }

    private static Map<String, Object> delivery(WebhookDelivery delivery) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", delivery.getId());
        map.put("webhook_id", delivery.getWebhook() == null ? null : delivery.getWebhook().getId());
        map.put("event", delivery.getEventType());
        map.put("payload", payload(delivery.getPayload()));
        map.put("response_code", delivery.getResponseStatus());
        map.put("status", delivery.getStatus());
        map.put("attempts", delivery.getAttemptCount());
        map.put("error", delivery.getErrorMessage());
        map.put("created_at", UiPresenter.iso(delivery.getCreatedAt()));
        return map;
    }

    private static Object payload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, Object.class);
        } catch (JsonProcessingException notJson) {
            return json;
        }
    }

    private static List<Map<String, Object>> actions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException unreadable) {
            return List.of();
        }
    }

    private static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException unwritable) {
            throw new IllegalStateException(unwritable);
        }
    }

    private Macro findMacro(Long id) {
        return macros.findById(id).orElseThrow(() -> new EntityNotFoundException("Macro not found"));
    }

    private CannedResponse findCannedResponse(Long id) {
        return cannedResponses.findById(id).orElseThrow(() -> new EntityNotFoundException("Canned response not found"));
    }

    private Webhook findWebhook(Long id) {
        return webhooks.findById(id).orElseThrow(() -> new EntityNotFoundException("Webhook not found"));
    }
}
