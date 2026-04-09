package dev.escalated.services;

import java.util.*;
import java.util.regex.*;

public class WorkflowEngine {

    public static final List<String> OPERATORS = List.of("equals", "not_equals", "contains", "not_contains", "starts_with", "ends_with", "greater_than", "less_than", "greater_or_equal", "less_or_equal", "is_empty", "is_not_empty");
    public static final List<String> ACTION_TYPES = List.of("change_status", "assign_agent", "change_priority", "add_tag", "remove_tag", "set_department", "add_note", "send_webhook", "set_type", "delay", "add_follower", "send_notification");
    public static final List<String> TRIGGER_EVENTS = List.of("ticket.created", "ticket.updated", "ticket.status_changed", "ticket.assigned", "ticket.priority_changed", "ticket.tagged", "ticket.department_changed", "reply.created", "reply.agent_reply", "sla.warning", "sla.breached", "ticket.reopened");

    @SuppressWarnings("unchecked")
    public boolean evaluateConditions(Map<String, Object> conditions, Map<String, String> ticket) {
        if (conditions.containsKey("all")) {
            List<Map<String, String>> all = (List<Map<String, String>>) conditions.get("all");
            return all.stream().allMatch(c -> evaluateSingle(c, ticket));
        }
        if (conditions.containsKey("any")) {
            List<Map<String, String>> any = (List<Map<String, String>>) conditions.get("any");
            return any.stream().anyMatch(c -> evaluateSingle(c, ticket));
        }
        return true;
    }

    public boolean evaluateSingle(Map<String, String> condition, Map<String, String> ticket) {
        String field = condition.getOrDefault("field", "");
        String operator = condition.getOrDefault("operator", "equals");
        String expected = condition.getOrDefault("value", "");
        String actual = ticket.getOrDefault(field, "");
        return applyOperator(operator, actual, expected);
    }

    public static boolean applyOperator(String operator, String actual, String expected) {
        return switch (operator) {
            case "equals" -> actual.equals(expected);
            case "not_equals" -> !actual.equals(expected);
            case "contains" -> actual.contains(expected);
            case "not_contains" -> !actual.contains(expected);
            case "starts_with" -> actual.startsWith(expected);
            case "ends_with" -> actual.endsWith(expected);
            case "greater_than" -> parseDouble(actual) > parseDouble(expected);
            case "less_than" -> parseDouble(actual) < parseDouble(expected);
            case "greater_or_equal" -> parseDouble(actual) >= parseDouble(expected);
            case "less_or_equal" -> parseDouble(actual) <= parseDouble(expected);
            case "is_empty" -> actual.isBlank();
            case "is_not_empty" -> !actual.isBlank();
            default -> false;
        };
    }

    public static String interpolateVariables(String text, Map<String, String> ticket) {
        Matcher matcher = Pattern.compile("\\{\\{(\\w+)\\}\\}").matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = ticket.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }
}
