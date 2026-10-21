package dev.escalated.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.events.EventNames;
import dev.escalated.models.Workflow;
import dev.escalated.repositories.WorkflowLogRepository;
import dev.escalated.repositories.WorkflowRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The workflow admin surface, per escalated-developer-context
 * {@code domain-model/workflow-admin-contract.md}: list, create, update,
 * toggle, reorder and delete, accepting the shared builder's body and
 * answering with workflow objects (snake_case, {@code conditions} as an object
 * and {@code actions} as an array).
 */
@Service
public class WorkflowAdminService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkflowRepository workflows;
    private final WorkflowLogRepository logs;

    public WorkflowAdminService(WorkflowRepository workflows, WorkflowLogRepository logs) {
        this.workflows = workflows;
        this.logs = logs;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Map<String, Object>> list() {
        return workflows.findAllByOrderByPositionAscNameAsc().stream().map(WorkflowAdminService::view).toList();
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> show(Long id) {
        return view(find(id));
    }

    /** The builder's option lists: the {@code Form} page props other than {@code workflow}. */
    public Map<String, Object> options() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("trigger_events", WorkflowEngine.TRIGGER_EVENTS.stream()
                .map(name -> option(name, EventNames.label(name)))
                .toList());
        options.put("action_types", WorkflowEngine.ACTIONS.entrySet().stream()
                .map(entry -> option(entry.getKey(), entry.getValue()))
                .toList());
        options.put("operators", WorkflowEngine.OPERATORS);
        options.put("condition_fields", WorkflowEngine.CONDITION_FIELDS);
        return options;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> create(Map<String, Object> body) {
        Workflow workflow = new Workflow();
        apply(workflow, body);
        workflow.setPosition(workflows.findAll().stream().mapToInt(Workflow::getPosition).max().orElse(-1) + 1);
        return view(workflows.save(workflow));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        Workflow workflow = find(id);
        apply(workflow, body);
        return view(workflows.save(workflow));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> toggle(Long id) {
        Workflow workflow = find(id);
        workflow.setActive(!workflow.isActive());
        return view(workflows.save(workflow));
    }

    /** Positions follow the order of {@code workflowIds}; unknown ids are ignored. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public List<Map<String, Object>> reorder(List<?> workflowIds) {
        if (workflowIds == null) {
            throw new ValidationFailedException(Map.of("workflow_ids", List.of("The workflow ids field is required.")));
        }
        int position = 0;
        for (Object raw : workflowIds) {
            Long id = asLong(raw);
            if (id == null) {
                continue;
            }
            int at = position++;
            workflows.findById(id).ifPresent(workflow -> {
                workflow.setPosition(at);
                workflows.save(workflow);
            });
        }
        return list();
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void delete(Long id) {
        Workflow workflow = find(id);
        // The table cascades, but a schema Hibernate built does not.
        logs.deleteAll(logs.findByWorkflowIdWithRelations(id));
        workflows.delete(workflow);
    }

    private Workflow find(Long id) {
        return workflows.findById(id).orElseThrow(() -> new EntityNotFoundException("Workflow not found: " + id));
    }

    /** Validates the builder body and copies it onto the workflow. */
    private void apply(Workflow workflow, Map<String, Object> body) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, Object> input = body == null ? Map.of() : body;

        Object name = input.get("name");
        if (!(name instanceof String text) || text.isBlank()) {
            errors.put("name", List.of("The name field is required."));
        } else if (text.length() > 255) {
            errors.put("name", List.of("The name may not be greater than 255 characters."));
        }

        Object trigger = input.containsKey("trigger_event") ? input.get("trigger_event") : input.get("trigger");
        String canonicalTrigger = trigger instanceof String text ? EventNames.canonical(text) : null;
        if (canonicalTrigger == null || canonicalTrigger.isBlank()) {
            errors.put("trigger_event", List.of("The trigger event field is required."));
        } else if (!WorkflowEngine.TRIGGER_EVENTS.contains(canonicalTrigger)) {
            errors.put("trigger_event", List.of("The selected trigger event is invalid."));
        }

        Map<String, Object> conditions = conditions(input.get("conditions"), errors);
        List<Map<String, Object>> actions = actions(input.get("actions"), errors);

        Object description = input.get("description");
        if (description != null && !(description instanceof String)) {
            errors.put("description", List.of("The description must be a string."));
        }

        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        workflow.setName(((String) name).trim());
        workflow.setDescription((String) description);
        workflow.setTriggerEvent(canonicalTrigger);
        workflow.setConditions(json(conditions));
        workflow.setActions(json(actions));
        if (input.containsKey("is_active")) {
            workflow.setActive(truthy(input.get("is_active")));
        }
        if (input.containsKey("stop_on_match")) {
            workflow.setStopOnMatch(truthy(input.get("stop_on_match")));
        }
        if (input.containsKey("position") && asLong(input.get("position")) != null) {
            workflow.setPosition(asLong(input.get("position")).intValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> conditions(Object raw, Map<String, List<String>> errors) {
        if (raw == null) {
            return Map.of("all", List.of());
        }
        if (!(raw instanceof Map<?, ?> map) || map.size() != 1
                || !(map.containsKey("all") || map.containsKey("any"))) {
            errors.put("conditions", List.of("The conditions must have exactly one of all or any."));
            return null;
        }
        String key = map.containsKey("all") ? "all" : "any";
        Object list = map.get(key);
        if (list == null) {
            return Map.of(key, List.of());
        }
        if (!(list instanceof List<?> items)) {
            errors.put("conditions." + key, List.of("The conditions must be a list."));
            return null;
        }
        List<Map<String, Object>> normalised = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Map<?, ?> condition)) {
                errors.put("conditions." + key + "." + i, List.of("Each condition must be an object."));
                continue;
            }
            Object field = condition.get("field");
            Object operator = condition.get("operator");
            if (!(field instanceof String fieldName) || !WorkflowEngine.CONDITION_FIELDS.contains(fieldName)) {
                errors.put("conditions." + key + "." + i + ".field", List.of("The selected field is invalid."));
                continue;
            }
            if (!(operator instanceof String op) || !WorkflowEngine.OPERATORS.contains(op)) {
                errors.put("conditions." + key + "." + i + ".operator",
                        List.of("The selected operator is invalid."));
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("field", fieldName);
            entry.put("operator", op);
            Object value = condition.get("value");
            // The account condition carries a {type, id} pair; everything
            // else is a scalar, stored as a string.
            if ("account".equals(fieldName) && value instanceof Map<?, ?>) {
                entry.put("value", value);
            } else {
                entry.put("value", value == null ? "" : String.valueOf(value));
            }
            normalised.add(entry);
        }
        return Map.of(key, normalised);
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
            Object type = action.get("type");
            if (!(type instanceof String name) || !WorkflowEngine.ACTION_TYPES.contains(name)) {
                errors.put("actions." + i + ".type", List.of("The selected action type is invalid."));
                continue;
            }
            Object value = action.get("value");
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", name);
            entry.put("value", value == null ? "" : String.valueOf(value));
            normalised.add(entry);
        }
        return normalised;
    }

    /** A workflow object as the contract describes it. */
    public static Map<String, Object> view(Workflow workflow) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", workflow.getId());
        view.put("name", workflow.getName());
        view.put("description", workflow.getDescription());
        view.put("trigger_event", workflow.getTriggerEvent());
        view.put("trigger", workflow.getTriggerEvent());
        view.put("conditions", WorkflowEngine.normalizeConditions(parse(workflow.getConditions())));
        Object actions = parse(workflow.getActions());
        view.put("actions", actions instanceof List<?> ? actions : List.of());
        view.put("is_active", workflow.isActive());
        view.put("stop_on_match", workflow.isStopOnMatch());
        view.put("position", workflow.getPosition());
        view.put("created_at", workflow.getCreatedAt());
        view.put("updated_at", workflow.getUpdatedAt());
        return view;
    }

    private static Object parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception unreadable) {
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

    private static Map<String, Object> option(String value, String label) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        return option;
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && List.of("1", "true", "on", "yes").contains(String.valueOf(value).toLowerCase());
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
