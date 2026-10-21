package dev.escalated.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.Automation;
import dev.escalated.models.RuleExecution;
import dev.escalated.repositories.AutomationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin CRUD for automations, taking what the shared
 * {@code Escalated/Admin/Automations/Form} posts: {@code name},
 * {@code conditions} ({@code [{field, operator, value}]}), {@code actions}
 * ({@code [{type, value}]}), {@code active} and {@code repeat_after_hours}.
 * Validation follows Laravel's {@code AutomationController}.
 */
@Service
public class AutomationAdminService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AutomationRepository automations;
    private final RuleFireLog fireLog;

    public AutomationAdminService(AutomationRepository automations, RuleFireLog fireLog) {
        this.automations = automations;
        this.fireLog = fireLog;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Map<String, Object>> list() {
        return automations.findAllByOrderByPositionAscIdAsc().stream().map(AutomationAdminService::view).toList();
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> show(Long id) {
        return view(find(id));
    }

    /** The vocabulary the runner understands, for a form. */
    public Map<String, Object> options() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("condition_fields", AutomationRunner.CONDITION_FIELDS);
        options.put("time_fields", AutomationRunner.TIME_FIELDS);
        options.put("operators", AutomationRunner.OPERATORS);
        options.put("action_types", AutomationRunner.ACTION_TYPES);
        return options;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> create(Map<String, Object> body) {
        Automation automation = new Automation();
        apply(automation, body == null ? Map.of() : body, true);
        automation.setPosition(automations.findAll().stream().mapToInt(Automation::getPosition).max().orElse(-1) + 1);
        return view(automations.save(automation));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        Automation automation = find(id);
        apply(automation, body == null ? Map.of() : body, false);
        return view(automations.save(automation));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> toggle(Long id) {
        Automation automation = find(id);
        automation.setActive(!automation.isActive());
        return view(automations.save(automation));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void delete(Long id) {
        Automation automation = find(id);
        automations.delete(automation);
        fireLog.forget(RuleExecution.AUTOMATION, id);
    }

    private Automation find(Long id) {
        return automations.findById(id).orElseThrow(() -> new EntityNotFoundException("Automation not found: " + id));
    }

    private void apply(Automation automation, Map<String, Object> input, boolean creating) {
        Map<String, List<String>> errors = new LinkedHashMap<>();

        Object name = input.get("name");
        if (!(name instanceof String text) || text.isBlank()) {
            errors.put("name", List.of("The name field is required."));
        } else if (text.length() > 255) {
            errors.put("name", List.of("The name may not be greater than 255 characters."));
        }

        List<Map<String, Object>> conditions = conditions(input.get("conditions"), errors);
        List<Map<String, Object>> actions = actions(input.get("actions"), errors);

        Object active = input.get("active");
        if (active != null && !(active instanceof Boolean)) {
            errors.put("active", List.of("The active field must be true or false."));
        }

        Integer repeat = null;
        Object rawRepeat = input.get("repeat_after_hours");
        if (rawRepeat != null && !(rawRepeat instanceof String blank && blank.isBlank())) {
            repeat = integer(rawRepeat);
            if (repeat == null || repeat < 1 || repeat > 8760) {
                errors.put("repeat_after_hours",
                        List.of("The repeat after hours must be a whole number between 1 and 8760."));
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        automation.setName(((String) name).trim());
        automation.setConditions(json(conditions));
        automation.setActions(json(actions));
        automation.setActive(active == null || (Boolean) active);
        // Only touched when sent, so an older form keeps the stored value.
        if (creating || input.containsKey("repeat_after_hours")) {
            automation.setRepeatAfterHours(repeat);
        }
    }

    private static List<Map<String, Object>> conditions(Object raw, Map<String, List<String>> errors) {
        if (!(raw instanceof List<?> items) || items.isEmpty()) {
            errors.put("conditions", List.of("The conditions field must have at least one condition."));
            return null;
        }
        List<Map<String, Object>> normalised = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Map<?, ?> condition)) {
                errors.put("conditions." + i, List.of("Each condition must be an object."));
                continue;
            }
            if (!(condition.get("field") instanceof String field) || !AutomationRunner.CONDITION_FIELDS.contains(field)) {
                errors.put("conditions." + i + ".field", List.of("The selected field is invalid."));
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("field", field);
            Object value = condition.get("value");
            if (AutomationRunner.TIME_FIELDS.contains(field)) {
                Object operator = condition.get("operator") == null ? ">" : condition.get("operator");
                if (!AutomationRunner.OPERATORS.contains(String.valueOf(operator))) {
                    errors.put("conditions." + i + ".operator", List.of("The selected operator is invalid."));
                    continue;
                }
                Integer hours = integer(value);
                if (hours == null || hours < 0) {
                    errors.put("conditions." + i + ".value", List.of("The hours must be a whole number."));
                    continue;
                }
                entry.put("operator", String.valueOf(operator));
                entry.put("value", hours);
            } else {
                if (condition.get("operator") != null) {
                    entry.put("operator", String.valueOf(condition.get("operator")));
                }
                entry.put("value", value == null ? "" : String.valueOf(value));
            }
            normalised.add(entry);
        }
        return normalised;
    }

    private static List<Map<String, Object>> actions(Object raw, Map<String, List<String>> errors) {
        if (!(raw instanceof List<?> items) || items.isEmpty()) {
            errors.put("actions", List.of("The actions field must have at least one action."));
            return null;
        }
        List<Map<String, Object>> normalised = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Map<?, ?> action)) {
                errors.put("actions." + i, List.of("Each action must be an object."));
                continue;
            }
            if (!(action.get("type") instanceof String type) || !AutomationRunner.ACTION_TYPES.contains(type)) {
                errors.put("actions." + i + ".type", List.of("The selected action type is invalid."));
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", type);
            entry.put("value", action.get("value") == null ? "" : String.valueOf(action.get("value")));
            normalised.add(entry);
        }
        return normalised;
    }

    public static Map<String, Object> view(Automation automation) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", automation.getId());
        view.put("name", automation.getName());
        view.put("conditions", AutomationRunner.list(automation.getConditions()));
        view.put("actions", AutomationRunner.list(automation.getActions()));
        view.put("active", automation.isActive());
        view.put("position", automation.getPosition());
        view.put("repeat_after_hours", automation.getRepeatAfterHours());
        view.put("last_run_at", automation.getLastRunAt());
        view.put("created_at", automation.getCreatedAt());
        view.put("updated_at", automation.getUpdatedAt());
        return view;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            double d = number.doubleValue();
            return d == Math.rint(d) ? (int) d : null;
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
