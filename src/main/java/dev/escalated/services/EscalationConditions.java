package dev.escalated.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.escalated.models.Ticket;
import dev.escalated.models.TicketStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * An escalation rule's conditions, as escalated-laravel's {@code EscalationService}: a list of
 * {@code {field, value}}, all of which must hold. Fields: {@code status}, {@code priority},
 * {@code assigned} ({@code unassigned} or anything else for assigned), {@code age_hours},
 * {@code no_response_hours}, {@code sla_breached}, {@code department_id}, {@code ticket_type},
 * {@code account} ({@code {type, id}}), {@code account_type} and {@code account_id}. An unknown or
 * blank field is ignored, as on Laravel.
 *
 * <p>This port adds three, for rules converted from its older shape (migration V18):
 * {@code sla_policy_id}; {@code sla_breached} narrowed by the value {@code first_response} or
 * {@code resolution} (any other value means either); and {@code workflow_conditions}, whose value
 * is a workflow condition object ({@code {all: [...]}}) evaluated with workflow semantics.
 */
public final class EscalationConditions {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The fields the shared rule form offers, plus this port's additions. */
    public static final List<String> FIELDS = List.of("status", "priority", "assigned", "age_hours",
            "no_response_hours", "sla_breached", "department_id", "ticket_type", "account", "account_type",
            "account_id", "sla_policy_id", "workflow_conditions");

    private EscalationConditions() {}

    /** The stored conditions as a list of maps; unreadable JSON is null. */
    public static List<Map<String, Object>> read(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            Object parsed = JSON.readValue(json, Object.class);
            if (!(parsed instanceof List<?> items)) {
                return null;
            }
            List<Map<String, Object>> conditions = new ArrayList<>();
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    conditions.add(JSON.convertValue(map, new TypeReference<Map<String, Object>>() {}));
                }
            }
            return conditions;
        } catch (JsonProcessingException malformed) {
            return null;
        }
    }

    public static boolean matches(String json, Ticket ticket, Instant now) {
        List<Map<String, Object>> conditions = read(json);
        if (conditions == null) {
            return false;
        }
        for (Map<String, Object> condition : conditions) {
            if (!holds(condition, ticket, now)) {
                return false;
            }
        }
        return true;
    }

    private static boolean holds(Map<String, Object> condition, Ticket ticket, Instant now) {
        Object field = condition.get("field");
        Object value = condition.get("value");
        String text = value == null ? null : String.valueOf(value).trim();
        return switch (field == null ? "" : String.valueOf(field)) {
            case "status" -> ticket.getStatus() != null && ticket.getStatus() == status(text);
            case "priority" -> ticket.getPriority() != null && text != null
                    && ticket.getPriority().name().equalsIgnoreCase(text);
            case "assigned" -> "unassigned".equals(text)
                    ? ticket.getAssignedAgent() == null
                    : ticket.getAssignedAgent() != null;
            case "age_hours" -> olderThan(ticket.getCreatedAt(), text, now);
            case "no_response_hours" -> ticket.getFirstRespondedAt() == null
                    && olderThan(ticket.getCreatedAt(), text, now);
            case "sla_breached" -> breached(ticket, text, now);
            case "department_id" -> ticket.getDepartment() != null && text != null
                    && String.valueOf(ticket.getDepartment().getId()).equals(text);
            case "ticket_type" -> text != null && text.equals(ticket.getTicketType());
            case "account" -> value instanceof Map<?, ?> pair && pair.get("type") != null && pair.get("id") != null
                    && String.valueOf(pair.get("type")).equals(ticket.getAccountType())
                    && String.valueOf(pair.get("id")).equals(ticket.getAccountId());
            case "account_type" -> text != null && text.equals(ticket.getAccountType());
            case "account_id" -> text != null && text.equals(ticket.getAccountId());
            case "sla_policy_id" -> ticket.getSlaPolicy() != null && text != null
                    && String.valueOf(ticket.getSlaPolicy().getId()).equals(text);
            case "workflow_conditions" -> workflowConditions(value, ticket);
            default -> true;
        };
    }

    private static boolean breached(Ticket ticket, String which, Instant now) {
        boolean firstResponse = ticket.getSlaFirstResponseDueAt() != null && ticket.getFirstRespondedAt() == null
                && !ticket.getSlaFirstResponseDueAt().isAfter(now);
        boolean resolution = ticket.getSlaDueAt() != null && !ticket.getSlaDueAt().isAfter(now);
        if ("first_response".equals(which)) {
            return firstResponse;
        }
        if ("resolution".equals(which)) {
            return resolution;
        }
        return firstResponse || resolution;
    }

    private static boolean olderThan(Instant createdAt, String hours, Instant now) {
        if (createdAt == null || hours == null) {
            return false;
        }
        try {
            long whole = Long.parseLong(hours);
            return !createdAt.isAfter(now.minusSeconds(whole * 3600));
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    private static boolean workflowConditions(Object value, Ticket ticket) {
        if (value == null) {
            return true;
        }
        try {
            String json = value instanceof String text ? text : JSON.writeValueAsString(value);
            return TicketRoutingConditions.matches(json, ticket);
        } catch (JsonProcessingException unreadable) {
            return false;
        }
    }

    /** A status as the pages name it; the frontend's waiting states are this port's pending. */
    private static TicketStatus status(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String name = value.toLowerCase(Locale.ROOT);
        if ("waiting_on_customer".equals(name) || "waiting_on_agent".equals(name)) {
            return TicketStatus.PENDING;
        }
        for (TicketStatus status : TicketStatus.values()) {
            if (Objects.equals(status.name().toLowerCase(Locale.ROOT), name)) {
                return status;
            }
        }
        return null;
    }
}
