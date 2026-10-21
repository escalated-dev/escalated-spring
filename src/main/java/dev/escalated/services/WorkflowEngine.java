package dev.escalated.services;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;

/**
 * Stateless evaluator for Workflow conditions and action shapes.
 *
 * <p>Registered as a component because {@code WorkflowRunnerService} takes it as
 * a constructor dependency. It carried no stereotype before, so no application
 * context that component-scanned this package could start -- the whole test
 * suite is Mockito and slice tests, so nothing ever tried.
 */
@Component
public class WorkflowEngine {

    public static final List<String> OPERATORS =
            List.of(
                    "equals",
                    "not_equals",
                    "contains",
                    "not_contains",
                    "starts_with",
                    "ends_with",
                    "greater_than",
                    "less_than",
                    "greater_or_equal",
                    "less_or_equal",
                    "is_empty",
                    "is_not_empty");
    public static final List<String> ACTION_TYPES =
            List.of(
                    "change_status",
                    "assign_agent",
                    "change_priority",
                    "add_tag",
                    "remove_tag",
                    "set_department",
                    "add_note",
                    "send_webhook",
                    "set_type",
                    "delay",
                    "add_follower",
                    "send_notification");
    /**
     * Every trigger this package fires: the canonical names of the shared
     * event vocabulary ({@link dev.escalated.events.EventNames#EMITTED}).
     * Workflows saved under a legacy name still run (see
     * {@link WorkflowRunnerService}).
     */
    public static final List<String> TRIGGER_EVENTS = dev.escalated.events.EventNames.EMITTED;

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
        if ("account".equals(field)) {
            Object raw = ((Map<?, ?>) condition).get("value");
            if (!(raw instanceof Map<?, ?> pair)
                    || !(pair.get("type") instanceof String type)
                    || pair.get("id") == null
                    || type.isBlank()
                    || String.valueOf(pair.get("id")).isBlank()
                    || !(pair.get("id") instanceof String || pair.get("id") instanceof Number)) {
                return false;
            }
            if (!List.of("equals", "not_equals").contains(operator)) {
                return false;
            }
            String actualType = ticket.get("account_type");
            String actualId = ticket.get("account_id");
            if (actualType == null
                    || actualType.isBlank()
                    || actualId == null
                    || actualId.isBlank()) {
                return false;
            }
            boolean equal =
                    type.equals(actualType) && String.valueOf(pair.get("id")).equals(actualId);
            return "equals".equals(operator) ? equal : !equal;
        }
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
