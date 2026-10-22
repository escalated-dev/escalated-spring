package dev.escalated.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.Department;
import dev.escalated.models.EscalationRule;
import dev.escalated.models.RuleExecution;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.EscalationRuleRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.EscalationActions;
import dev.escalated.services.EscalationConditions;
import dev.escalated.services.RuleFireLog;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SLA policy and escalation rule pages' props and writes, as escalated-laravel's
 * {@code Admin\SlaPolicyController} and {@code Admin\EscalationRuleController}, validated as its
 * {@code StoreSlaPolicyRequest} and {@code StoreEscalationRuleRequest}.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class AdminSlaUiPages {

    /** Laravel's {@code Ticket::TYPES}, offered by the policy form; this port also stores others. */
    static final List<String> TICKET_TYPES = List.of("question", "problem", "incident", "task");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SlaPolicyRepository policies;
    private final EscalationRuleRepository rules;
    private final TicketRepository tickets;
    private final DepartmentRepository departments;
    private final RuleFireLog fireLog;
    private final EscalatedProperties properties;

    public AdminSlaUiPages(SlaPolicyRepository policies, EscalationRuleRepository rules, TicketRepository tickets,
                           DepartmentRepository departments, RuleFireLog fireLog, EscalatedProperties properties) {
        this.policies = policies;
        this.rules = rules;
        this.tickets = tickets;
        this.departments = departments;
        this.fireLog = fireLog;
        this.properties = properties;
    }

    // ── SLA policies ────────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> policies() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : tickets.countBySlaPolicy()) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SlaPolicy policy : policies.findAll(org.springframework.data.domain.Sort.by("id"))) {
            Map<String, Object> row = policy(policy);
            row.put("tickets_count", counts.getOrDefault(policy.getId(), 0L));
            rows.add(row);
        }
        return Map.of("policies", rows);
    }

    /** The form's props: Laravel's options, plus {@code policy} when editing. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> policyForm(Long id) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (id != null) {
            props.put("policy", policy(findPolicy(id)));
        }
        List<String> priorities = new ArrayList<>();
        for (TicketPriority priority : TicketPriority.values()) {
            priorities.add(priority.name().toLowerCase(Locale.ROOT));
        }
        props.put("priorities", priorities);
        props.put("targeted", properties != null && properties.isTargetedSlas());
        List<Map<String, Object>> active = new ArrayList<>();
        for (Department department : departments.findByActiveTrueOrderBySortOrderAsc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", department.getId());
            row.put("name", department.getName());
            active.add(row);
        }
        props.put("departments", active);
        props.put("ticketTypes", TICKET_TYPES);
        return props;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void savePolicy(Long id, UiInput input) {
        String name = input.requiredString("name", 255);
        Map<String, Double> firstResponse = hours(input, "first_response_hours");
        Map<String, Double> resolution = hours(input, "resolution_hours");
        Long departmentId = input.longValue("department_id");
        if (departmentId != null && !departments.existsById(departmentId)) {
            throw UiInput.invalid("department_id", "The selected department id is invalid.");
        }
        String ticketType = input.string("ticket_type");
        if (ticketType != null && ticketType.length() > 100) {
            throw UiInput.invalid("ticket_type", "The ticket type field must not be greater than 100 characters.");
        }

        SlaPolicy policy = id == null ? new SlaPolicy() : findPolicy(id);
        policy.setName(name);
        if (input.has("description")) {
            policy.setDescription(input.string("description"));
        }
        policy.setFirstResponseHours(firstResponse);
        policy.setResolutionHours(resolution);
        policy.setDefaultPolicy(input.bool("is_default", policy.isDefaultPolicy()));
        policy.setBusinessHoursOnly(input.bool("business_hours_only", policy.isBusinessHoursOnly()));
        policy.setActive(input.bool("is_active", policy.isActive()));
        if (input.has("department_id")) {
            policy.setDepartmentId(departmentId);
        }
        if (input.has("ticket_type")) {
            policy.setTicketType(ticketType);
        }
        policies.save(policy);
    }

    /** Deletes the policy; its tickets keep their deadlines and lose the policy, as ON DELETE SET NULL. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deletePolicy(Long id) {
        SlaPolicy policy = findPolicy(id);
        tickets.detachSlaPolicy(policy.getId());
        policies.delete(policy);
    }

    // ── Escalation rules ────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> rules() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EscalationRule rule : rules.findAllByOrderBySortOrderAscIdAsc()) {
            rows.add(rule(rule));
        }
        return Map.of("rules", rows);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> ruleForm(Long id) {
        return id == null ? Map.of() : Map.of("rule", rule(findRule(id)));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void saveRule(Long id, UiInput input) {
        String name = input.requiredString("name", 255);
        String triggerType = input.requiredString("trigger_type", 255);
        List<Map<String, Object>> conditions = items(input, "conditions");
        List<Map<String, Object>> actions = items(input, "actions");
        Long order = input.longValue("order");
        Long repeat = input.longValue("repeat_after_hours");
        if (repeat != null && (repeat < 1 || repeat > 8760)) {
            throw UiInput.invalid("repeat_after_hours", "The repeat after hours field must be between 1 and 8760.");
        }

        EscalationRule rule = id == null ? new EscalationRule() : findRule(id);
        rule.setName(name);
        if (input.has("description")) {
            rule.setDescription(input.string("description"));
        }
        rule.setTriggerType(triggerType);
        rule.setConditions(write(conditions));
        rule.setActions(write(actions));
        if (order != null) {
            rule.setSortOrder(order.intValue());
        }
        rule.setActive(input.bool("is_active", rule.isActive()));
        if (input.has("repeat_after_hours")) {
            rule.setRepeatAfterHours(repeat == null ? null : repeat.intValue());
        }
        rules.save(rule);
    }

    /** Deletes the rule and its fire log, as Laravel's {@code EscalationRule::deleted}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteRule(Long id) {
        EscalationRule rule = findRule(id);
        rules.delete(rule);
        fireLog.forget(RuleExecution.ESCALATION_RULE, rule.getId());
    }

    // ── Shapes ──────────────────────────────────────────────────────────

    static Map<String, Object> policy(SlaPolicy policy) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", policy.getId());
        row.put("name", policy.getName());
        row.put("description", policy.getDescription());
        row.put("is_default", policy.isDefaultPolicy());
        row.put("first_response_hours", numbers(policy.getFirstResponseHours()));
        row.put("resolution_hours", numbers(policy.getResolutionHours()));
        row.put("business_hours_only", policy.isBusinessHoursOnly());
        row.put("is_active", policy.isActive());
        row.put("department_id", policy.getDepartmentId());
        row.put("ticket_type", policy.getTicketType());
        row.put("created_at", UiPresenter.iso(policy.getCreatedAt()));
        row.put("updated_at", UiPresenter.iso(policy.getUpdatedAt()));
        return row;
    }

    static Map<String, Object> rule(EscalationRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rule.getId());
        row.put("name", rule.getName());
        row.put("description", rule.getDescription());
        row.put("category", rule.getCategory());
        row.put("trigger_type", rule.getTriggerType());
        List<Map<String, Object>> conditions = EscalationConditions.read(rule.getConditions());
        row.put("conditions", conditions == null ? List.of() : conditions);
        row.put("actions", EscalationActions.read(rule.getActions()));
        row.put("order", rule.getSortOrder());
        row.put("is_active", rule.isActive());
        row.put("repeat_after_hours", rule.getRepeatAfterHours());
        row.put("created_at", UiPresenter.iso(rule.getCreatedAt()));
        row.put("updated_at", UiPresenter.iso(rule.getUpdatedAt()));
        return row;
    }

    /** Whole hours as integers ({@code 4}, not {@code 4.0}), as Laravel's JSON has them. */
    private static Map<String, Object> numbers(Map<String, Double> hours) {
        Map<String, Object> shown = new LinkedHashMap<>();
        hours.forEach((priority, value) -> shown.put(priority,
                value == Math.rint(value) && Math.abs(value) < Long.MAX_VALUE ? (Object) value.longValue() : value));
        return shown;
    }

    /** A {@code {priority: hours}} map: required and non-empty, known priorities, non-negative numbers. */
    private static Map<String, Double> hours(UiInput input, String key) {
        Object raw = input.raw(key);
        String label = key.replace('_', ' ');
        if (raw == null || raw instanceof Map<?, ?> empty && empty.isEmpty()) {
            throw UiInput.invalid(key, "The " + label + " field is required.");
        }
        if (!(raw instanceof Map<?, ?> given)) {
            throw UiInput.invalid(key, "The " + label + " field must be an array.");
        }
        Map<String, Double> hours = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : given.entrySet()) {
            String priority = String.valueOf(entry.getKey()).trim().toLowerCase(Locale.ROOT);
            boolean known = false;
            for (TicketPriority candidate : TicketPriority.values()) {
                known |= candidate.name().equalsIgnoreCase(priority);
            }
            if (!known) {
                throw UiInput.invalid(key + "." + entry.getKey(), "The selected priority is invalid.");
            }
            Object value = entry.getValue();
            if (value == null || value instanceof String text && text.isBlank()) {
                continue;
            }
            double number;
            try {
                number = value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException notANumber) {
                throw UiInput.invalid(key + "." + priority, "The " + label + "." + priority + " field must be a number.");
            }
            if (number < 0 || Double.isNaN(number) || Double.isInfinite(number)) {
                throw UiInput.invalid(key + "." + priority, "The " + label + "." + priority + " field must be at least 0.");
            }
            hours.put(priority, number);
        }
        return hours;
    }

    /** A required, non-empty list of objects, stored as given. */
    private static List<Map<String, Object>> items(UiInput input, String key) {
        List<Object> given = input.list(key);
        if (given == null || given.isEmpty()) {
            throw UiInput.invalid(key, "The " + key + " field is required.");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (int index = 0; index < given.size(); index++) {
            if (!(given.get(index) instanceof Map<?, ?> item)) {
                throw UiInput.invalid(key + "." + index, "The " + key + "." + index + " field must be an array.");
            }
            Map<String, Object> stored = new LinkedHashMap<>();
            item.forEach((k, v) -> stored.put(String.valueOf(k), v));
            items.add(stored);
        }
        return items;
    }

    private static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private SlaPolicy findPolicy(Long id) {
        return policies.findById(id).orElseThrow(() -> new EntityNotFoundException("SLA policy not found: " + id));
    }

    private EscalationRule findRule(Long id) {
        return rules.findById(id).orElseThrow(() -> new EntityNotFoundException("Escalation rule not found: " + id));
    }
}
