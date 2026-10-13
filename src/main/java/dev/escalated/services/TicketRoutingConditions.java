package dev.escalated.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.escalated.models.Ticket;

import java.util.Map;

/** Shared by escalation rules and scheduled automations, with workflow semantics. */
public final class TicketRoutingConditions {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TicketRoutingConditions() {}

    public static boolean matches(String json, Ticket ticket) {
        if (json == null || json.isBlank()) {
            return true;
        }
        try {
            Map<String, Object> conditions = MAPPER.readValue(json, new TypeReference<>() {});
            return new WorkflowEngine()
                    .evaluateConditions(
                            conditions, WorkflowRunnerService.ticketToConditionMap(ticket));
        } catch (Exception ex) {
            return false;
        }
    }
}
