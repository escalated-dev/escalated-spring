package dev.escalated.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.SavedView;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.SavedViewRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.TicketService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin ticket pages' props, bulk actions and saved views, as escalated-laravel's
 * {@code Admin\TicketController}, {@code BulkActionController} and {@code Admin\SavedViewController}.
 * The queue and the ticket are the agent pages' ({@link AgentUiPages}) plus what the admin pages add:
 * {@code agents}, {@code savedViews} and {@code is_snoozed}.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class AdminTicketUiPages {

    /** The bulk actions BulkActionBar sends. */
    public static final Set<String> BULK_ACTIONS = Set.of("status", "priority", "assign", "tags", "department", "delete");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int BULK_LIMIT = 50;

    private final AgentUiPages agentPages;
    private final AgentProfileRepository agents;
    private final SavedViewRepository savedViews;
    private final TicketRepository ticketRepository;
    private final TagRepository tags;
    private final TicketService tickets;

    @org.springframework.beans.factory.annotation.Autowired
    private dev.escalated.services.TicketStatuses statuses;

    public AdminTicketUiPages(AgentUiPages agentPages, AgentProfileRepository agents, SavedViewRepository savedViews,
                              TicketRepository ticketRepository, TagRepository tags, TicketService tickets) {
        this.agentPages = agentPages;
        this.agents = agents;
        this.savedViews = savedViews;
        this.ticketRepository = ticketRepository;
        this.tags = tags;
        this.tickets = tickets;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> index(Map<String, String> filters, Long agentId) {
        Map<String, Object> props = agentPages.index(filters, agentId, "escalated.admin.tickets.index");
        props.put("agents", agents());
        props.put("savedViews", savedViews(agentId));
        return props;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> show(Long ticketId, Long agentId) {
        Map<String, Object> props = agentPages.show(ticketId, agentId);
        props.remove("customActions");
        @SuppressWarnings("unchecked")
        Map<String, Object> ticket = (Map<String, Object>) props.get("ticket");
        boolean snoozed = isSnoozed(ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found")));
        ticket.put("is_snoozed", snoozed);
        props.put("agents", agents());
        props.put("is_snoozed", snoozed);
        return props;
    }

    /** Staff a ticket can go to: active agents and admins, as {@code {id, name, email}}. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Map<String, Object>> agents() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AgentProfile agent : agents.findByActiveTrueOrderByName()) {
            if (agent.isAgent() || agent.isAdmin()) {
                list.add(UiPresenter.agent(agent));
            }
        }
        return list;
    }

    // ── Bulk actions ────────────────────────────────────────────────────

    /**
     * Applies one action to each ticket, as Laravel's {@code BulkActionController}: a ticket the
     * action fails on is skipped. Answers how many were changed.
     */
    public int bulk(UiInput input, String actorEmail) {
        if (!input.has("ticket_ids") || input.longs("ticket_ids").isEmpty()) {
            throw UiInput.invalid("ticket_ids", "The ticket ids field is required.");
        }
        List<Long> ids = input.longs("ticket_ids");
        if (ids.size() > BULK_LIMIT) {
            throw UiInput.invalid("ticket_ids", "The ticket ids field must not have more than " + BULK_LIMIT + " items.");
        }
        String action = input.string("action");
        if (action == null) {
            throw UiInput.invalid("action", "The action field is required.");
        }
        if (!BULK_ACTIONS.contains(action)) {
            throw UiInput.invalid("action", "The selected action is invalid.");
        }
        Object value = input.raw("value");
        if (!"delete".equals(action) && (value == null || String.valueOf(value).isBlank())) {
            throw UiInput.invalid("value", "The value field is required unless action is in delete.");
        }

        int changed = 0;
        for (Long id : ids) {
            if (ticketRepository.findById(id).isEmpty()) {
                continue;
            }
            try {
                if (apply(id, action, value, actorEmail)) {
                    changed++;
                }
            } catch (RuntimeException skipped) {
                // As Laravel: a ticket the action cannot apply to is skipped.
            }
        }
        return changed;
    }

    private boolean apply(Long id, String action, Object value, String actorEmail) {
        switch (action) {
            case "status" -> {
                // A built-in status, or a custom status slug.
                if (value == null || !statuses.isKnown(String.valueOf(value))) {
                    return false;
                }
                tickets.changeStatusByName(id, String.valueOf(value), actorEmail);
            }
            case "priority" -> {
                TicketPriority priority = UiTicketFilters.parsePriority(String.valueOf(value));
                if (priority == null) {
                    return false;
                }
                tickets.update(id, null, null, priority);
            }
            case "assign" -> {
                Long agentId = UiTicketFilters.parseLong(String.valueOf(value));
                if (agentId == null || agents.findById(agentId).filter(AgentProfile::isActive).isEmpty()) {
                    return false;
                }
                tickets.assign(id, agentId, actorEmail);
            }
            case "tags" -> {
                List<Object> given = value instanceof List<?> list ? new ArrayList<>(list) : List.of(value);
                for (Object item : given) {
                    Long tagId = UiTicketFilters.parseLong(String.valueOf(item));
                    Tag tag = tagId == null ? null : tags.findById(tagId).orElse(null);
                    if (tag != null) {
                        tickets.addTag(id, tag.getName());
                    }
                }
            }
            case "department" -> {
                Long departmentId = UiTicketFilters.parseLong(String.valueOf(value));
                if (departmentId == null) {
                    return false;
                }
                tickets.changeDepartment(id, departmentId, actorEmail);
            }
            case "delete" -> tickets.delete(id, actorEmail);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ── Saved views ─────────────────────────────────────────────────────

    /** The agent's own views and the shared ones. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Map<String, Object>> savedViews(Long agentId) {
        List<Map<String, Object>> list = new ArrayList<>();
        List<SavedView> views = agentId == null
                ? savedViews.findAll().stream().filter(SavedView::isShared).toList()
                : savedViews.findAccessibleByAgent(agentId);
        for (SavedView view : views) {
            list.add(savedView(view));
        }
        return list;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> storeSavedView(UiInput input, Long agentId) {
        SavedView view = new SavedView();
        fill(view, input);
        if (agentId != null) {
            view.setAgent(agents.getReferenceById(agentId));
        }
        return savedView(savedViews.save(view));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> updateSavedView(Long id, UiInput input, Long agentId) {
        SavedView view = ownedView(id, agentId);
        fill(view, input);
        return savedView(savedViews.save(view));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteSavedView(Long id, Long agentId) {
        savedViews.delete(ownedView(id, agentId));
    }

    /** A view someone else saved may be applied, not changed. */
    private SavedView ownedView(Long id, Long agentId) {
        SavedView view = savedViews.findById(id).orElseThrow(() -> new EntityNotFoundException("Saved view not found"));
        if (view.getAgent() != null && !view.getAgent().getId().equals(agentId)) {
            throw new AccessDeniedException("This view belongs to someone else.");
        }
        return view;
    }

    private static void fill(SavedView view, UiInput input) {
        view.setName(input.requiredString("name", 255));
        if (!(input.raw("filters") instanceof Map<?, ?> filters)) {
            throw UiInput.invalid("filters", input.has("filters")
                    ? "The filters field must be an array." : "The filters field is required.");
        }
        try {
            view.setFilters(JSON.writeValueAsString(filters));
        } catch (JsonProcessingException unwritable) {
            throw UiInput.invalid("filters", "The filters field must be an array.");
        }
        if (input.has("is_shared")) {
            view.setShared(input.bool("is_shared", false));
        }
        if (input.has("is_default")) {
            view.setDefaultView(input.bool("is_default", false));
        }
    }

    private static Map<String, Object> savedView(SavedView view) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", view.getId());
        map.put("name", view.getName());
        map.put("filters", filters(view.getFilters()));
        map.put("sort_by", view.getSortBy());
        map.put("sort_dir", view.getSortDirection());
        map.put("is_shared", view.isShared());
        map.put("is_default", view.isDefaultView());
        map.put("user_id", view.getAgent() == null ? null : view.getAgent().getId());
        map.put("color", null);
        map.put("icon", null);
        return map;
    }

    private static Object filters(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, Object.class);
        } catch (JsonProcessingException unreadable) {
            return Map.of();
        }
    }

    static boolean isSnoozed(Ticket ticket) {
        return ticket.getSnoozedUntil() != null && ticket.getSnoozedUntil().isAfter(Instant.now());
    }
}
