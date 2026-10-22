package dev.escalated.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.dtos.admin.AgentSkillEntryDto;
import dev.escalated.dtos.admin.CreateSkillDto;
import dev.escalated.dtos.admin.UpdateSkillDto;
import dev.escalated.models.AgentCapacity;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.AuditLog;
import dev.escalated.models.BusinessSchedule;
import dev.escalated.models.Holiday;
import dev.escalated.models.Skill;
import dev.escalated.repositories.AgentCapacityRepository;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AgentSkillRepository;
import dev.escalated.repositories.AuditLogRepository;
import dev.escalated.repositories.BusinessScheduleRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.SkillRepository;
import dev.escalated.repositories.SkillRoutingDepartmentRepository;
import dev.escalated.repositories.SkillRoutingTagRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.services.SkillService;
import dev.escalated.services.ValidationFailedException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The routing and audit admin pages' props and writes, as escalated-laravel's
 * {@code Admin\SkillController}, {@code CapacityController}, {@code BusinessHoursController} and
 * {@code AuditLogController}: {@code Escalated/Admin/Skills/Index} and {@code Form},
 * {@code Capacity/Index}, {@code BusinessHours/Index} and {@code Form}, and {@code AuditLog/Index}.
 *
 * <p>Differences from Laravel, where this package's data differs: an agent's capacity has no
 * channel here (one limit per agent; the page gets {@code "default"}, Laravel's default channel);
 * the audit log's actor is recorded by email, so {@code user} is the agent with that email, and
 * {@code actions} lists the actions recorded so far ({@code create}, {@code delete}, ...) rather
 * than Laravel's fixed three.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class AdminRoutingUiPages {

    static final List<String> DAYS =
            List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    private static final int AUDIT_LOGS_PER_PAGE = 50;
    private static final List<String> AUDIT_FILTERS =
            List.of("user_id", "action", "auditable_type", "date_from", "date_to");
    private static final Set<String> ZONE_REGIONS = Set.of("Africa", "America", "Antarctica", "Arctic", "Asia",
            "Atlantic", "Australia", "Europe", "Indian", "Pacific");
    private static final Pattern TIME = Pattern.compile("^(([01]\\d|2[0-3]):[0-5]\\d|24:00)$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SkillService skillService;
    private final SkillRepository skills;
    private final AgentSkillRepository agentSkills;
    private final SkillRoutingTagRepository routingTags;
    private final SkillRoutingDepartmentRepository routingDepartments;
    private final TagRepository tags;
    private final DepartmentRepository departments;
    private final AgentProfileRepository agents;
    private final AgentCapacityRepository capacities;
    private final BusinessScheduleRepository schedules;
    private final SlaPolicyRepository slaPolicies;
    private final AuditLogRepository auditLogs;
    private final UiRoutes routes;

    public AdminRoutingUiPages(SkillService skillService, SkillRepository skills, AgentSkillRepository agentSkills,
                               SkillRoutingTagRepository routingTags,
                               SkillRoutingDepartmentRepository routingDepartments, TagRepository tags,
                               DepartmentRepository departments, AgentProfileRepository agents,
                               AgentCapacityRepository capacities, BusinessScheduleRepository schedules,
                               SlaPolicyRepository slaPolicies, AuditLogRepository auditLogs, UiRoutes routes) {
        this.skillService = skillService;
        this.skills = skills;
        this.agentSkills = agentSkills;
        this.routingTags = routingTags;
        this.routingDepartments = routingDepartments;
        this.tags = tags;
        this.departments = departments;
        this.agents = agents;
        this.capacities = capacities;
        this.schedules = schedules;
        this.slaPolicies = slaPolicies;
        this.auditLogs = auditLogs;
        this.routes = routes;
    }

    // ── Skills ──────────────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> skills() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Skill skill : skills.findAll(Sort.by("name"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", skill.getId());
            row.put("name", skill.getName());
            row.put("slug", skill.getSlug());
            row.put("agents_count", agentSkills.countBySkill_Id(skill.getId()));
            row.put("routing_tags_count", routingTags.countBySkill_Id(skill.getId()));
            row.put("routing_departments_count", routingDepartments.countBySkill_Id(skill.getId()));
            row.put("updated_at", UiPresenter.iso(skill.getUpdatedAt()));
            rows.add(row);
        }
        return Map.of("skills", rows);
    }

    /** The form's props: the agents, tags and departments to pick from, and {@code skill} when editing. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> skillForm(Long id) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<Map<String, Object>> available = new ArrayList<>();
        for (AgentProfile agent : agents.findByActiveTrueAndAgentTrueOrderByName()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", agent.getId());
            row.put("name", agent.getName());
            row.put("email", agent.getEmail());
            available.add(row);
        }
        props.put("availableAgents", available);
        List<Map<String, Object>> tagRows = new ArrayList<>();
        tags.findAll(Sort.by("name")).forEach(tag -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", tag.getId());
            row.put("name", tag.getName());
            row.put("color", tag.getColor());
            tagRows.add(row);
        });
        props.put("availableTags", tagRows);
        List<Map<String, Object>> departmentRows = new ArrayList<>();
        departments.findAll(Sort.by("name")).forEach(department -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", department.getId());
            row.put("name", department.getName());
            departmentRows.add(row);
        });
        props.put("availableDepartments", departmentRows);
        if (id != null) {
            Skill skill = skills.findById(id).orElseThrow(() -> new EntityNotFoundException("Skill not found"));
            Map<String, Object> shown = new LinkedHashMap<>();
            shown.put("id", skill.getId());
            shown.put("name", skill.getName());
            shown.put("slug", skill.getSlug());
            shown.put("routing_tag_ids", routingTags.findBySkill_Id(id).stream()
                    .map(link -> link.getTag().getId()).sorted().toList());
            shown.put("routing_department_ids", routingDepartments.findBySkill_Id(id).stream()
                    .map(link -> link.getDepartment().getId()).sorted().toList());
            List<Map<String, Object>> assigned = new ArrayList<>();
            agentSkills.findBySkill_Id(id).stream()
                    .sorted(Comparator.comparing(link -> link.getUserId()))
                    .forEach(link -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("user_id", link.getUserId());
                        row.put("proficiency", link.getProficiency());
                        assigned.add(row);
                    });
            shown.put("agents", assigned);
            props.put("skill", shown);
        }
        return props;
    }

    /** Validates as Laravel's {@code SkillController::validatePayload} and saves through the skill service. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void saveSkill(Long id, UiInput input) {
        if (id != null && !skills.existsById(id)) {
            throw new EntityNotFoundException("Skill not found");
        }
        String name = input.requiredString("name", 255);
        if (id == null ? skills.existsByName(name) : skills.existsByNameAndIdNot(name, id)) {
            throw UiInput.invalid("name", "The name has already been taken.");
        }
        Map<String, List<String>> errors = new LinkedHashMap<>();
        List<Long> tagIds = ids(input, "routing_tag_ids", errors, tags::existsById);
        List<Long> departmentIds = ids(input, "routing_department_ids", errors, departments::existsById);
        List<AgentSkillEntryDto> entries = skillAgents(input, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        CreateSkillDto dto = id == null ? new CreateSkillDto() : new UpdateSkillDto();
        dto.setName(name);
        if (id != null) {
            dto.setDescription(skills.findById(id).map(Skill::getDescription).orElse(null));
        }
        dto.setRoutingTagIds(tagIds);
        dto.setRoutingDepartmentIds(departmentIds);
        dto.setAgents(entries);
        if (id == null) {
            skillService.create(dto);
        } else {
            skillService.update(id, (UpdateSkillDto) dto);
        }
    }

    public void deleteSkill(Long id) {
        skillService.delete(id);
    }

    private static List<Long> ids(UiInput input, String key, Map<String, List<String>> errors,
                                  java.util.function.Predicate<Long> exists) {
        Object raw = input.raw(key);
        if (raw != null && !(raw instanceof List<?>)) {
            errors.put(key, List.of("The " + key.replace('_', ' ') + " field must be an array."));
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        List<Object> items = input.list(key);
        for (int index = 0; items != null && index < items.size(); index++) {
            Long value = whole(items.get(index));
            String field = key + "." + index;
            if (value == null) {
                errors.put(field, List.of("The " + field + " field must be an integer."));
            } else if (!exists.test(value)) {
                errors.put(field, List.of("The selected " + field + " is invalid."));
            } else {
                ids.add(value);
            }
        }
        return ids;
    }

    private List<AgentSkillEntryDto> skillAgents(UiInput input, Map<String, List<String>> errors) {
        Object raw = input.raw("agents");
        if (raw != null && !(raw instanceof List<?>)) {
            errors.put("agents", List.of("The agents field must be an array."));
            return List.of();
        }
        List<AgentSkillEntryDto> entries = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<Object> items = input.list("agents");
        for (int index = 0; items != null && index < items.size(); index++) {
            Map<?, ?> item = items.get(index) instanceof Map<?, ?> map ? map : Map.of();
            String userField = "agents." + index + ".user_id";
            String proficiencyField = "agents." + index + ".proficiency";
            Long userId = whole(item.get("user_id"));
            Long proficiency = whole(item.get("proficiency"));
            if (item.get("user_id") == null) {
                errors.put(userField, List.of("The " + userField + " field is required."));
            } else if (userId == null) {
                errors.put(userField, List.of("The " + userField + " field must be an integer."));
            } else if (!seen.add(userId)) {
                errors.put(userField, List.of("The " + userField + " field has a duplicate value."));
            } else {
                AgentProfile agent = agents.findById(userId).orElse(null);
                if (agent == null) {
                    errors.put(userField, List.of("The selected " + userField + " is invalid."));
                } else if (!agent.isAgent()) {
                    errors.put(userField, List.of("The selected " + userField + " is not an agent."));
                }
            }
            if (item.get("proficiency") == null) {
                errors.put(proficiencyField, List.of("The " + proficiencyField + " field is required."));
            } else if (proficiency == null || proficiency < 1 || proficiency > 5) {
                errors.put(proficiencyField, List.of("The " + proficiencyField + " field must be between 1 and 5."));
            }
            if (userId != null && proficiency != null) {
                AgentSkillEntryDto entry = new AgentSkillEntryDto();
                entry.setUserId(userId);
                entry.setProficiency(proficiency.intValue());
                entries.add(entry);
            }
        }
        return entries;
    }

    // ── Capacity ────────────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> capacity() {
        List<AgentCapacity> all = new ArrayList<>(capacities.findAll());
        all.sort(Comparator.comparing((AgentCapacity capacity) -> capacity.getAgent().getId())
                .thenComparing(AgentCapacity::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgentCapacity capacity : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", capacity.getId());
            row.put("user_id", capacity.getAgent().getId());
            String name = capacity.getAgent().getName();
            row.put("agent_name", name == null || name.isBlank() ? "Unknown" : name);
            row.put("channel", "default");
            row.put("max_concurrent", capacity.getMaxTickets());
            row.put("current_count", capacity.getCurrentTickets());
            row.put("load_percentage", capacity.getMaxTickets() <= 0 ? 100.0
                    : Math.round(capacity.getCurrentTickets() * 1000.0 / capacity.getMaxTickets()) / 10.0);
            rows.add(row);
        }
        return Map.of("capacities", rows);
    }

    /** Validates as Laravel's {@code CapacityController::update}: {@code max_concurrent} 1 to 999. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void updateCapacity(Long id, UiInput input) {
        AgentCapacity capacity = capacities.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Capacity not found"));
        long max = input.requiredLong("max_concurrent");
        if (max < 1 || max > 999) {
            throw UiInput.invalid("max_concurrent", "The max concurrent field must be between 1 and 999.");
        }
        capacity.setMaxTickets((int) max);
        capacities.save(capacity);
    }

    // ── Business hours ──────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> businessHours() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BusinessSchedule schedule : schedules.findAll(Sort.by("id"))) {
            rows.add(schedule(schedule));
        }
        return Map.of("schedules", rows);
    }

    /** The form's props: {@code timezones}, and {@code schedule} when editing. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> businessHoursForm(Long id) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (id != null) {
            props.put("schedule", schedule(findSchedule(id)));
        }
        props.put("timezones", timezones());
        return props;
    }

    /** Validates as Laravel's {@code BusinessHoursController}, saves, and replaces the holidays. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void saveBusinessHours(Long id, UiInput input) {
        BusinessSchedule schedule = id == null ? new BusinessSchedule() : findSchedule(id);
        String name = input.requiredString("name", 255);
        String timezone = input.string("timezone");
        if (timezone == null) {
            throw UiInput.invalid("timezone", "The timezone field is required.");
        }
        if (!timezones().contains(timezone)) {
            throw UiInput.invalid("timezone", "The timezone field must be a valid timezone.");
        }
        Object hours = input.raw("schedule");
        if (hours == null) {
            throw UiInput.invalid("schedule", "The schedule field is required.");
        }
        if (!(hours instanceof Map<?, ?> week)) {
            throw UiInput.invalid("schedule", "The schedule field must be an array.");
        }
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, String[]> days = new HashMap<>();
        for (String day : DAYS) {
            Map<?, ?> times = week.get(day) instanceof Map<?, ?> map ? map : Map.of();
            String start = time(times.get("start"), "schedule." + day + ".start", errors);
            String end = time(times.get("end"), "schedule." + day + ".end", errors);
            days.put(day, start == null || end == null ? new String[] {null, null} : new String[] {start, end});
        }
        List<Holiday> holidays = holidays(input, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        boolean makeDefault = input.bool("is_default", false);
        if (makeDefault) {
            for (BusinessSchedule other : schedules.findByDefaultScheduleTrue()) {
                if (!other.getId().equals(schedule.getId())) {
                    other.setDefaultSchedule(false);
                    schedules.save(other);
                }
            }
        }
        schedule.setName(name);
        schedule.setTimezone(timezone);
        schedule.setDefaultSchedule(makeDefault);
        schedule.setMondayStart(days.get("monday")[0]);
        schedule.setMondayEnd(days.get("monday")[1]);
        schedule.setTuesdayStart(days.get("tuesday")[0]);
        schedule.setTuesdayEnd(days.get("tuesday")[1]);
        schedule.setWednesdayStart(days.get("wednesday")[0]);
        schedule.setWednesdayEnd(days.get("wednesday")[1]);
        schedule.setThursdayStart(days.get("thursday")[0]);
        schedule.setThursdayEnd(days.get("thursday")[1]);
        schedule.setFridayStart(days.get("friday")[0]);
        schedule.setFridayEnd(days.get("friday")[1]);
        schedule.setSaturdayStart(days.get("saturday")[0]);
        schedule.setSaturdayEnd(days.get("saturday")[1]);
        schedule.setSundayStart(days.get("sunday")[0]);
        schedule.setSundayEnd(days.get("sunday")[1]);
        schedule.getHolidays().clear();
        for (Holiday holiday : holidays) {
            holiday.setBusinessSchedule(schedule);
            schedule.getHolidays().add(holiday);
        }
        schedules.save(schedule);
    }

    /** Deletes a schedule; the policies that followed it follow the default again. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteBusinessHours(Long id) {
        BusinessSchedule schedule = findSchedule(id);
        slaPolicies.detachSchedule(id);
        schedules.delete(schedule);
    }

    private BusinessSchedule findSchedule(Long id) {
        return schedules.findById(id).orElseThrow(() -> new EntityNotFoundException("Business schedule not found"));
    }

    private static Map<String, Object> schedule(BusinessSchedule schedule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", schedule.getId());
        map.put("name", schedule.getName());
        map.put("timezone", schedule.getTimezone());
        map.put("is_default", schedule.isDefaultSchedule());
        Map<String, Object> week = new LinkedHashMap<>();
        week.put("monday", hours(schedule.getMondayStart(), schedule.getMondayEnd()));
        week.put("tuesday", hours(schedule.getTuesdayStart(), schedule.getTuesdayEnd()));
        week.put("wednesday", hours(schedule.getWednesdayStart(), schedule.getWednesdayEnd()));
        week.put("thursday", hours(schedule.getThursdayStart(), schedule.getThursdayEnd()));
        week.put("friday", hours(schedule.getFridayStart(), schedule.getFridayEnd()));
        week.put("saturday", hours(schedule.getSaturdayStart(), schedule.getSaturdayEnd()));
        week.put("sunday", hours(schedule.getSundayStart(), schedule.getSundayEnd()));
        map.put("schedule", week);
        List<Map<String, Object>> holidays = new ArrayList<>();
        schedule.getHolidays().stream().sorted(Comparator.comparing(Holiday::getDate)).forEach(holiday -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", holiday.getId());
            row.put("name", holiday.getName());
            row.put("date", holiday.getDate().toString());
            row.put("recurring", holiday.isRecurring());
            holidays.add(row);
        });
        map.put("holidays", holidays);
        map.put("created_at", UiPresenter.iso(schedule.getCreatedAt()));
        map.put("updated_at", UiPresenter.iso(schedule.getUpdatedAt()));
        return map;
    }

    /** A day as the schedule editor reads it: {@code {start, end}}, empty strings when closed. */
    private static Map<String, Object> hours(String start, String end) {
        Map<String, Object> day = new LinkedHashMap<>();
        boolean open = start != null && end != null;
        day.put("start", open ? start : "");
        day.put("end", open ? end : "");
        return day;
    }

    private static String time(Object value, String field, Map<String, List<String>> errors) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (!TIME.matcher(text).matches()) {
            errors.put(field, List.of("The " + field + " field must match the format H:i."));
            return null;
        }
        return text;
    }

    private static List<Holiday> holidays(UiInput input, Map<String, List<String>> errors) {
        Object raw = input.raw("holidays");
        if (raw != null && !(raw instanceof List<?>)) {
            errors.put("holidays", List.of("The holidays field must be an array."));
            return List.of();
        }
        List<Holiday> holidays = new ArrayList<>();
        List<Object> items = input.list("holidays");
        for (int index = 0; items != null && index < items.size(); index++) {
            Map<?, ?> item = items.get(index) instanceof Map<?, ?> map ? map : Map.of();
            String nameField = "holidays." + index + ".name";
            String dateField = "holidays." + index + ".date";
            Object name = item.get("name");
            if (name == null || String.valueOf(name).isBlank()) {
                errors.put(nameField, List.of("The " + nameField + " field is required."));
            } else if (String.valueOf(name).length() > 255) {
                errors.put(nameField, List.of("The " + nameField + " field must not be greater than 255 characters."));
            }
            LocalDate date = null;
            Object given = item.get("date");
            if (given == null || String.valueOf(given).isBlank()) {
                errors.put(dateField, List.of("The " + dateField + " field is required."));
            } else {
                date = date(String.valueOf(given).trim());
                if (date == null) {
                    errors.put(dateField, List.of("The " + dateField + " field must be a valid date."));
                }
            }
            if (name != null && date != null) {
                Holiday holiday = new Holiday();
                holiday.setName(String.valueOf(name).trim());
                holiday.setDate(date);
                Object recurring = item.get("recurring");
                holiday.setRecurring(Boolean.TRUE.equals(recurring)
                        || "1".equals(String.valueOf(recurring)) || "true".equals(String.valueOf(recurring)));
                holidays.add(holiday);
            }
        }
        return holidays;
    }

    /** {@code 2026-07-01}, or the date of a serialized datetime ({@code 2026-07-01T00:00:00Z}). */
    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value.contains("T") ? value.substring(0, value.indexOf('T')) : value);
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    /** PHP's {@code timezone_identifiers_list()}: the regional IANA zones, and UTC. */
    static List<String> timezones() {
        List<String> zones = new ArrayList<>();
        for (String zone : ZoneId.getAvailableZoneIds()) {
            int slash = zone.indexOf('/');
            if (slash > 0 && ZONE_REGIONS.contains(zone.substring(0, slash))) {
                zones.add(zone);
            }
        }
        zones.add("UTC");
        zones.sort(String::compareTo);
        return zones;
    }

    // ── Audit log ───────────────────────────────────────────────────────

    /** Laravel's filters: {@code user_id}, {@code action}, {@code auditable_type}, {@code date_from}, {@code date_to}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> auditLog(Map<String, String> query) {
        Map<String, String> filters = new LinkedHashMap<>();
        for (String key : AUDIT_FILTERS) {
            if (query.containsKey(key)) {
                filters.put(key, query.get(key));
            }
        }

        Specification<AuditLog> where = (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            String userId = filled(filters.get("user_id"));
            if (userId != null) {
                AgentProfile actor = null;
                try {
                    actor = agents.findById(Long.parseLong(userId)).orElse(null);
                } catch (NumberFormatException notAnId) {
                    // An unknown user matches nothing.
                }
                predicates.add(actor == null ? builder.disjunction()
                        : builder.equal(root.get("actorEmail"), actor.getEmail()));
            }
            if (filled(filters.get("action")) != null) {
                predicates.add(builder.equal(root.get("action"), filters.get("action").trim()));
            }
            if (filled(filters.get("auditable_type")) != null) {
                predicates.add(builder.equal(root.get("entityType"), filters.get("auditable_type").trim()));
            }
            LocalDate from = date(String.valueOf(filled(filters.get("date_from"))));
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"),
                        from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            LocalDate to = date(String.valueOf(filled(filters.get("date_to"))));
            if (to != null) {
                predicates.add(builder.lessThan(root.get("createdAt"),
                        to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        int page = 1;
        try {
            page = Math.max(Integer.parseInt(query.getOrDefault("page", "1")), 1);
        } catch (NumberFormatException notANumber) {
            // The first page.
        }
        Page<AuditLog> found = auditLogs.findAll(where, PageRequest.of(page - 1, AUDIT_LOGS_PER_PAGE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        Map<String, AgentProfile> actors = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AuditLog log : found) {
            AgentProfile actor = log.getActorEmail() == null ? null
                    : actors.computeIfAbsent(log.getActorEmail(), email -> agents.findByEmail(email).orElse(null));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("user_id", actor == null ? null : actor.getId());
            row.put("action", log.getAction());
            row.put("auditable_type", log.getEntityType());
            row.put("auditable_id", log.getEntityId());
            row.put("old_values", values(log.getOldValues()));
            row.put("new_values", values(log.getNewValues()));
            row.put("ip_address", log.getActorIp());
            row.put("created_at", UiPresenter.iso(log.getCreatedAt()));
            Map<String, Object> user = null;
            if (actor != null) {
                user = new LinkedHashMap<>();
                user.put("id", actor.getId());
                user.put("name", actor.getName());
            }
            row.put("user", user);
            rows.add(row);
        }

        Map<String, String> kept = new LinkedHashMap<>();
        filters.forEach((key, value) -> {
            if (filled(value) != null) {
                kept.put(key, value);
            }
        });
        List<Map<String, Object>> users = new ArrayList<>();
        for (AgentProfile user : agents.findAll(Sort.by("name"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("name", user.getName());
            users.add(row);
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("logs", UiPresenter.paginator(found, rows, routes.url("escalated.admin.audit-log"), kept));
        props.put("filters", filters);
        props.put("users", users);
        props.put("actions", auditLogs.distinctActions());
        props.put("resourceTypes", auditLogs.distinctEntityTypes());
        return props;
    }

    /** Stored JSON as an object for the page; anything else as it was stored. */
    private static Object values(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(stored, Object.class);
        } catch (JsonProcessingException notJson) {
            return stored;
        }
    }

    private static String filled(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Long whole(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue() == number.longValue() ? number.longValue() : null;
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
