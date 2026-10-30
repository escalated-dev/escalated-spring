package dev.escalated.ui;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.Department;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.Workflow;
import dev.escalated.models.WorkflowLog;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.repositories.WorkflowLogRepository;
import dev.escalated.repositories.WorkflowRepository;
import dev.escalated.services.SettingsService;
import dev.escalated.services.ValidationFailedException;
import dev.escalated.widget.WidgetOrigins;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin pages' props and the writes that have no admin service of their own: settings,
 * departments and tags, as escalated-laravel's {@code Admin\SettingsController},
 * {@code DepartmentController} and {@code TagController}.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class AdminUiPages {

    /**
     * The settings this port stores and honours: what {@code supported_settings} lists, so the
     * shared Settings page shows only these controls.
     */
    public static final List<String> SUPPORTED_SETTINGS = List.of(
            "allow_customer_close", "show_powered_by",
            "widget_color", "widget_position", "widget_greeting", WidgetOrigins.SETTING, "two_factor_required");

    private static final Set<String> BOOLEAN_SETTINGS = Set.of("allow_customer_close", "show_powered_by", "two_factor_required");
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final SettingsService settings;
    private final DepartmentRepository departments;
    private final TagRepository tags;
    private final TicketRepository tickets;
    private final WorkflowLogRepository workflowLogs;
    private final WorkflowRepository workflowRepository;

    /** How many executions the Logs page shows, newest first (Laravel's {@code LOGS_LIMIT}). */
    public static final int LOGS_LIMIT = 100;

    public AdminUiPages(SettingsService settings, DepartmentRepository departments, TagRepository tags,
                        TicketRepository tickets, WorkflowLogRepository workflowLogs,
                        WorkflowRepository workflowRepository) {
        this.settings = settings;
        this.departments = departments;
        this.tags = tags;
        this.tickets = tickets;
        this.workflowLogs = workflowLogs;
        this.workflowRepository = workflowRepository;
    }

    /** Tickets carrying the tag, by query: Hibernate cannot load a filtered tag's ticket collection. */
    private static Specification<Ticket> taggedWith(Long tagId) {
        return (root, query, cb) -> cb.equal(root.join("tags").get("id"), tagId);
    }

    private static Specification<Ticket> inDepartment(Long departmentId) {
        return (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
    }

    // ── Settings ────────────────────────────────────────────────────────

    public Map<String, Object> settings() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("allow_customer_close", bool("allow_customer_close", true));
        values.put("show_powered_by", bool("show_powered_by", true));
        values.put("widget_color", settings.getOrDefault("widget_color", "#4F46E5"));
        values.put("widget_position", settings.getOrDefault("widget_position", "bottom-right"));
        values.put("widget_greeting", settings.getOrDefault("widget_greeting", "Hi there! How can we help?"));
        values.put(WidgetOrigins.SETTING, settings.getOrDefault(WidgetOrigins.SETTING, ""));
        values.put("two_factor_required", bool("two_factor_required", false));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("settings", values);
        props.put("supported_settings", SUPPORTED_SETTINGS);
        return props;
    }

    /** Validates every supported key given, then saves them; keys this port does not store are ignored. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void saveSettings(UiInput input) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, String> save = new LinkedHashMap<>();
        for (String key : SUPPORTED_SETTINGS) {
            if (!input.has(key)) {
                continue;
            }
            Object raw = input.raw(key);
            String value = raw == null ? "" : String.valueOf(raw).trim();
            if (BOOLEAN_SETTINGS.contains(key)) {
                if (!Set.of("true", "false", "1", "0").contains(value.toLowerCase(java.util.Locale.ROOT))) {
                    errors.put(key, List.of("The " + key.replace('_', ' ') + " field must be true or false."));
                    continue;
                }
                save.put(key, input.bool(key, false) ? "1" : "0");
            } else if ("widget_color".equals(key) && !HEX_COLOR.matcher(value).matches()) {
                errors.put(key, List.of("The widget color field format is invalid."));
            } else if ("widget_position".equals(key) && !Set.of("bottom-right", "bottom-left").contains(value)) {
                errors.put(key, List.of("The selected widget position is invalid."));
            } else if ("widget_greeting".equals(key) && value.length() > 255) {
                errors.put(key, List.of("The widget greeting field must not be greater than 255 characters."));
            } else if (WidgetOrigins.SETTING.equals(key) && !WidgetOrigins.invalidEntries(value).isEmpty()) {
                errors.put(key, List.of("Not an origin such as https://shop.example.com: "
                        + String.join(", ", WidgetOrigins.invalidEntries(value))));
            } else {
                save.put(key, value);
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        save.forEach((key, value) -> settings.set(key, value, "general"));
    }

    // ── Workflow logs ───────────────────────────────────────────────────

    /**
     * The newest {@value #LOGS_LIMIT} workflow executions, all workflows or {@code workflowId}'s,
     * as Laravel's {@code WorkflowController::logs}: {@code actions_executed} is a count, the actions
     * go out as {@code action_details}; {@code workflows} feeds the page's filter.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> workflowLogs(Long workflowId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        PageRequest newest = PageRequest.of(0, LOGS_LIMIT);
        List<WorkflowLog> recent = workflowId == null ? workflowLogs.findRecent(newest)
                : workflowLogs.findRecentForWorkflow(workflowId, newest);
        for (WorkflowLog log : recent) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("workflow_id", log.getWorkflow() == null ? null : log.getWorkflow().getId());
            row.put("ticket_id", log.getTicket() == null ? null : log.getTicket().getId());
            row.put("trigger_event", log.getTriggerEvent());
            row.put("event", log.getEvent());
            row.put("workflow_name", log.getWorkflowName());
            row.put("ticket_reference", log.getTicketReference());
            row.put("conditions_matched", log.isConditionsMatched());
            row.put("matched", log.isMatched());
            row.put("actions_executed", log.getActionsExecutedCount());
            row.put("action_details", log.getActionDetails());
            row.put("error_message", log.getErrorMessage());
            row.put("status", log.getStatus());
            row.put("duration_ms", log.getDurationMs());
            row.put("started_at", UiPresenter.iso(log.getStartedAt()));
            row.put("completed_at", UiPresenter.iso(log.getCompletedAt()));
            row.put("created_at", UiPresenter.iso(log.getCreatedAt()));
            rows.add(row);
        }
        List<Map<String, Object>> options = new ArrayList<>();
        for (Workflow workflow : workflowRepository.findAllByOrderByPositionAscNameAsc()) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("id", workflow.getId());
            option.put("name", workflow.getName());
            options.add(option);
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("logs", rows);
        props.put("workflows", options);
        return props;
    }

    // ── Departments ─────────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> departments() {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Department> all = new ArrayList<>(departments.findAll());
        all.sort(Comparator.comparingInt(Department::getSortOrder).thenComparing(Department::getName));
        for (Department department : all) {
            Map<String, Object> row = department(department);
            row.put("tickets_count", tickets.count(inDepartment(department.getId())));
            row.put("agents_count", department.getAgents().size());
            rows.add(row);
        }
        return Map.of("departments", rows);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> department(Long id) {
        return Map.of("department", department(findDepartment(id)));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void saveDepartment(Long id, UiInput input) {
        String name = input.requiredString("name", 255);
        String description = input.string("description");
        Department department = id == null ? new Department() : findDepartment(id);
        departments.findByName(name).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw UiInput.invalid("name", "The name has already been taken.");
        });
        department.setName(name);
        department.setDescription(description);
        department.setActive(input.bool("is_active", true));
        departments.save(department);
    }

    /** Deletes the department; its tickets and agents are left without one. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteDepartment(Long id) {
        Department department = findDepartment(id);
        for (Ticket ticket : tickets.findAll(inDepartment(id))) {
            ticket.setDepartment(null);
        }
        department.getAgents().forEach(agent -> agent.setDepartment(null));
        departments.delete(department);
        departments.flush();
    }

    // ── Tags ────────────────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> tags() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tag tag : tags.findAll()) {
            Map<String, Object> row = UiPresenter.tag(tag);
            row.put("tickets_count", tickets.count(taggedWith(tag.getId())));
            rows.add(row);
        }
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("name"))));
        return Map.of("tags", rows);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void saveTag(Long id, UiInput input) {
        String name = input.requiredString("name", 255);
        String color = input.string("color");
        if (color != null && !HEX_COLOR.matcher(color).matches()) {
            throw UiInput.invalid("color", "The color field format is invalid.");
        }
        Tag tag = id == null ? new Tag() : findTag(id);
        tags.findByName(name).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw UiInput.invalid("name", "The name has already been taken.");
        });
        tag.setName(name);
        tag.setColor(color);
        tags.save(tag);
    }

    /** Deletes the tag and takes it off every ticket that has it. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteTag(Long id) {
        Tag tag = findTag(id);
        for (Ticket ticket : tickets.findAll(taggedWith(id))) {
            ticket.getTags().removeIf(given -> id.equals(given.getId()));
        }
        tickets.flush();
        tags.delete(tag);
        tags.flush();
    }

    private static Map<String, Object> department(Department department) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", department.getId());
        map.put("name", department.getName());
        map.put("description", department.getDescription());
        map.put("is_active", department.isActive());
        return map;
    }

    private Department findDepartment(Long id) {
        return departments.findById(id).orElseThrow(() -> new EntityNotFoundException("Department not found"));
    }

    private Tag findTag(Long id) {
        return tags.findById(id).orElseThrow(() -> new EntityNotFoundException("Tag not found"));
    }

    private boolean bool(String key, boolean fallback) {
        return settings.get(key).map(v -> "1".equals(v) || "true".equalsIgnoreCase(v)).orElse(fallback);
    }
}
