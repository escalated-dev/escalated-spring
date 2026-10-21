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
    /**
     * The actions {@link WorkflowExecutorService} performs, with builder
     * labels: the contract's core catalog plus {@code set_type},
     * {@code add_follower} and {@code delay}. These, and only these, are
     * offered to the builder.
     */
    public static final Map<String, String> ACTIONS;

    static {
        Map<String, String> actions = new LinkedHashMap<>();
        actions.put("change_status", "Change status");
        actions.put("change_priority", "Change priority");
        actions.put("add_tag", "Add tag");
        actions.put("remove_tag", "Remove tag");
        actions.put("set_department", "Set department");
        actions.put("assign_agent", "Assign agent");
        actions.put("add_note", "Add internal note");
        actions.put("insert_canned_reply", "Insert canned reply");
        actions.put("set_type", "Set ticket type");
        actions.put("add_follower", "Add follower");
        actions.put("delay", "Delay");
        ACTIONS = Collections.unmodifiableMap(actions);
    }

    public static final List<String> ACTION_TYPES = List.copyOf(ACTIONS.keySet());

    /**
     * Condition fields the engine evaluates: the contract's list plus the
     * account pair and a few ticket columns.
     */
    public static final List<String> CONDITION_FIELDS =
            List.of(
                    "status",
                    "priority",
                    "ticket_type",
                    "channel",
                    "subject",
                    "description",
                    "tags",
                    "department_id",
                    "assigned_to",
                    "hours_since_created",
                    "hours_since_updated",
                    "requester_email",
                    "requester_name",
                    "account");
    /**
     * Every trigger this package fires: the canonical names of the shared
     * event vocabulary ({@link dev.escalated.events.EventNames#EMITTED}).
     * Workflows saved under a legacy name still run (see
     * {@link WorkflowRunnerService}).
     */
    public static final List<String> TRIGGER_EVENTS = dev.escalated.events.EventNames.EMITTED;

    /**
     * Reads stored conditions in any shape a release has written: the
     * contract's {@code {all: [...]}} or {@code {any: [...]}}, Laravel's
     * {@code {match: 'all'|'any', rules: [...]}}, or a flat list (read as
     * {@code all}). Null means no conditions, which matches every ticket.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalizeConditions(Object stored) {
        if (stored == null) {
            return Map.of("all", List.of());
        }
        if (stored instanceof List<?> list) {
            return Map.of("all", list);
        }
        if (stored instanceof Map<?, ?> map) {
            if (map.containsKey("all") || map.containsKey("any")) {
                return (Map<String, Object>) map;
            }
            if (map.containsKey("rules")) {
                Object rules = map.get("rules") == null ? List.of() : map.get("rules");
                return Map.of("any".equals(String.valueOf(map.get("match"))) ? "any" : "all", rules);
            }
            if (map.isEmpty()) {
                return Map.of("all", List.of());
            }
        }
        return null;
    }

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
        // Values arrive from JSON and may be numbers or null, whatever the
        // declared type says.
        Map<?, ?> untyped = condition;
        String field = text(untyped.get("field"), "");
        String operator = text(untyped.get("operator"), "equals");
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
        String expected = text(untyped.get("value"), "");
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

    private static String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }
}
