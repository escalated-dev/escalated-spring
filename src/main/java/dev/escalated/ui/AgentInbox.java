package dev.escalated.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.SavedView;
import dev.escalated.repositories.SavedViewRepository;
import dev.escalated.repositories.TicketRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The agent panel's optional {@code inbox} and {@code queue} props (shared frontend
 * {@code docs/agent-panel.md}), as escalated-laravel's {@code AgentInbox} builds them.
 *
 * <p>Every number is a count over the same filters the ticket list applies
 * ({@link UiTicketFilters#specification}), through the ticket repository and so the department and
 * account visibility rules, so it is what the agent would see by opening that list. Rail counts
 * ({@code counts}, {@code views}) are of unresolved, unsnoozed tickets unless a view's filters name
 * a status; {@code stats} are for the current queue. They are kept per agent and queue for
 * {@code escalated.ui.agent-inbox-cache-seconds} (default 10) in this process.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class AgentInbox {

    /** The session attribute that keeps the queue last shown in the list. */
    public static final String SESSION_KEY = "escalated.agent_inbox.queue";

    /** Saved views beyond this many are not listed, so the counts stay bounded. */
    public static final int MAX_SAVED_VIEWS = 50;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> PRESENTATION_KEYS = List.of("page", "per_page", "view");
    private static final Map<String, String> QUEUE_TITLES =
            Map.of("mine", "My inbox", "unassigned", "Unassigned", "all", "All tickets");

    /** A queue: its key and title, and its own filters (what its counts and stats are over). */
    public record Queue(String key, String title, Map<String, Object> filters) {
        public Map<String, Object> prop() {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("key", key);
            prop.put("title", title);
            return prop;
        }
    }

    /** A view in the rail: its id, name and the list filters it links to. */
    public record View(Object id, String name, Map<String, Object> filters) {}

    private record Cached(Instant expires, Map<String, Object> inbox) {}

    private final TicketRepository tickets;
    private final SavedViewRepository savedViews;
    private final EscalatedProperties properties;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    public AgentInbox(TicketRepository tickets, SavedViewRepository savedViews, EscalatedProperties properties) {
        this.tickets = tickets;
        this.savedViews = savedViews;
        this.properties = properties;
    }

    /** The clock the cache reads; tests move it. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** The list's filters, with {@code assigned=me} and {@code assigned_to=me} as the agent's id. */
    public Map<String, String> normalise(Map<String, String> filters, Long agentId) {
        Map<String, String> out = new LinkedHashMap<>(filters);
        if (agentId != null && ("me".equals(out.get("assigned")) || "me".equals(out.get("assigned_to")))) {
            out.put("assigned_to", String.valueOf(agentId));
        }
        out.remove("assigned");
        return out;
    }

    /** The queue a list request shows. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Queue queueFor(Map<String, String> filters, Long agentId) {
        List<View> views = views(agentId);
        String viewId = filters.get("view");
        if (viewId != null && !viewId.isBlank()) {
            for (View view : views) {
                if (String.valueOf(view.id()).equals(viewId.trim())) {
                    return viewQueue(view);
                }
            }
        }
        Map<String, String> selection = comparable(filters);
        if (!selection.isEmpty()) {
            for (View view : views) {
                if (!view.filters().isEmpty() && comparable(view.filters()).equals(selection)) {
                    return viewQueue(view);
                }
            }
        }
        if (agentId != null && String.valueOf(agentId).equals(filters.get("assigned_to"))) {
            return workspaceQueue("mine", agentId);
        }
        if (UiTicketFilters.truthy(filters.get("unassigned"))) {
            return workspaceQueue("unassigned", agentId);
        }
        return workspaceQueue("all", agentId);
    }

    /** The filters the list applies: the request's, over a view picked by id. */
    public Map<String, String> listFilters(Map<String, String> filters, Queue queue) {
        String viewId = filters.get("view");
        if (viewId == null || viewId.isBlank() || !queue.key().startsWith("view:")) {
            return filters;
        }
        Map<String, String> out = strings(queue.filters());
        filters.forEach((key, value) -> {
            if (!"view".equals(key)) {
                out.put(key, value);
            }
        });
        return out;
    }

    /** Remembers the queue for the ticket page, which is opened from the list. */
    public void remember(HttpSession session, Queue queue) {
        if (session != null) {
            session.setAttribute(SESSION_KEY, queue.key());
        }
    }

    /** The queue last shown in the list, or All tickets. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Queue recalled(HttpSession session, Long agentId) {
        Object key = session == null ? null : session.getAttribute(SESSION_KEY);
        if (key instanceof String value && value.startsWith("view:")) {
            // The view may have been deleted or unshared since.
            String id = value.substring(5);
            return views(agentId).stream()
                    .filter(view -> String.valueOf(view.id()).equals(id))
                    .findFirst()
                    .map(AgentInbox::viewQueue)
                    .orElseGet(() -> workspaceQueue("all", agentId));
        }
        return workspaceQueue(key instanceof String value ? value : "all", agentId);
    }

    /** The {@code inbox} prop for this agent and queue. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> inbox(Long agentId, Queue queue) {
        int seconds = properties.getUi().getAgentInboxCacheSeconds();
        if (seconds <= 0) {
            return build(agentId, queue);
        }
        String key = agentId + "|" + queue.key() + "|" + new TreeMap<>(comparable(queue.filters()));
        Instant now = clock.instant();
        Cached hit = cache.get(key);
        if (hit != null && hit.expires().isAfter(now)) {
            return hit.inbox();
        }
        cache.values().removeIf(entry -> !entry.expires().isAfter(now));
        Map<String, Object> inbox = build(agentId, queue);
        cache.put(key, new Cached(now.plusSeconds(seconds), inbox));
        return inbox;
    }

    private Map<String, Object> build(Long agentId, Queue queue) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("mine", agentId == null ? 0L : countOpen(Map.of("assigned_to", agentId)));
        counts.put("unassigned", countOpen(Map.of("unassigned", 1)));
        counts.put("all", countOpen(Map.of()));

        List<Map<String, Object>> views = new ArrayList<>();
        for (View view : views(agentId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", view.id());
            row.put("name", view.name());
            row.put("count", countOpen(view.filters()));
            row.put("filters", view.filters());
            views.add(row);
        }

        // Each stat is the list the tile opens: the queue plus the tile's filters.
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("open", count(with(queue.filters(), Map.of("status", "open"))));
        stats.put("urgent", count(with(queue.filters(), Map.of("priority", "urgent", "open", 1))));
        stats.put("sla_at_risk", count(with(queue.filters(), Map.of("sla_at_risk", 1))));
        stats.put("resolved_today", count(with(queue.filters(), Map.of("status", "resolved", "resolved_today", 1))));

        // Of the same open tickets, those unread for this agent (ADR 2026-11-05-agent-panel-data).
        Map<String, Object> unread = new LinkedHashMap<>();
        unread.put("mine", agentId == null ? 0L : countUnread(Map.of("assigned_to", agentId), agentId));
        unread.put("unassigned", countUnread(Map.of("unassigned", 1), agentId));
        unread.put("all", countUnread(Map.of(), agentId));

        Map<String, Object> inbox = new LinkedHashMap<>();
        inbox.put("counts", counts);
        inbox.put("unread", unread);
        inbox.put("views", views);
        inbox.put("stats", stats);
        return inbox;
    }

    /** Built-in views, then the agent's own and shared saved views. */
    public List<View> views(Long agentId) {
        List<View> views = new ArrayList<>();
        views.add(new View("urgent", "Urgent", ordered("priority", "urgent", "open", 1)));
        views.add(new View("sla_at_risk", "SLA at risk", ordered("sla_at_risk", 1)));
        if (agentId != null) {
            views.add(new View("waiting_on_me", "Waiting on me",
                    ordered("assigned_to", agentId, "status", "waiting_on_agent")));
            views.add(new View("waiting_on_customer", "Waiting on customer",
                    ordered("assigned_to", agentId, "status", "waiting_on_customer")));
        }
        views.add(new View("recently_updated", "Recently updated",
                ordered("open", 1, "updated_within_hours", 24, "sort_by", "updated_at")));
        if (agentId == null) {
            return views;
        }
        savedViews.findAccessibleByAgent(agentId).stream()
                .sorted(Comparator.comparing(SavedView::getId))
                .limit(MAX_SAVED_VIEWS)
                .forEach(view -> views.add(new View(view.getId(), view.getName(), savedFilters(view, agentId))));
        return views;
    }

    private Map<String, Object> savedFilters(SavedView view, Long agentId) {
        Map<String, Object> filters = new LinkedHashMap<>();
        try {
            Object parsed = view.getFilters() == null || view.getFilters().isBlank()
                    ? Map.of() : JSON.readValue(view.getFilters(), Object.class);
            if (parsed instanceof Map<?, ?> map) {
                map.forEach((key, value) -> filters.put(String.valueOf(key), value));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            return filters;
        }
        if ("me".equals(filters.get("assigned")) || "me".equals(filters.get("assigned_to"))) {
            filters.put("assigned_to", agentId);
        }
        filters.remove("assigned");
        return filters;
    }

    private static Queue viewQueue(View view) {
        return new Queue("view:" + view.id(), view.name(), view.filters());
    }

    private static Queue workspaceQueue(String key, Long agentId) {
        if ("mine".equals(key) && agentId != null) {
            return new Queue("mine", QUEUE_TITLES.get("mine"), ordered("assigned_to", agentId));
        }
        if ("unassigned".equals(key)) {
            return new Queue("unassigned", QUEUE_TITLES.get("unassigned"), ordered("unassigned", 1));
        }
        return new Queue("all", QUEUE_TITLES.get("all"), new LinkedHashMap<>());
    }

    /** Unresolved, unsnoozed tickets, unless the filters ask for a status themselves. */
    private long countOpen(Map<String, Object> filters) {
        Object status = filters.get("status");
        if (status == null || String.valueOf(status).isBlank()) {
            return count(with(filters, Map.of("open", 1)));
        }
        return count(filters);
    }

    /** Of the open tickets {@link #countOpen} counts, those unread for the agent. */
    private long countUnread(Map<String, Object> filters, Long agentId) {
        if (agentId == null) {
            return 0;
        }
        Map<String, Object> open = with(filters, Map.of("open", 1));
        return tickets.count(UiTicketFilters.specification(strings(open), null)
                .and(dev.escalated.services.AgentPanelService.unreadFor(agentId)));
    }

    private long count(Map<String, Object> filters) {
        return tickets.count(UiTicketFilters.specification(strings(filters), null));
    }

    private static Map<String, Object> with(Map<String, Object> base, Map<String, Object> extra) {
        Map<String, Object> out = new LinkedHashMap<>(base);
        out.putAll(extra);
        return out;
    }

    private static Map<String, Object> ordered(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static Map<String, String> strings(Map<String, ?> filters) {
        Map<String, String> out = new LinkedHashMap<>();
        filters.forEach((key, value) -> {
            if (value instanceof List<?> list) {
                out.put(key, String.join(",", list.stream().map(String::valueOf).toList()));
            } else if (value != null) {
                out.put(key, String.valueOf(value));
            }
        });
        return out;
    }

    /** Filters as strings, without empty values or order, for comparing a URL with a view. */
    private static Map<String, String> comparable(Map<String, ?> filters) {
        Map<String, String> clean = new TreeMap<>();
        filters.forEach((key, value) -> {
            if (value == null || Boolean.FALSE.equals(value) || PRESENTATION_KEYS.contains(key)) {
                return;
            }
            String text;
            if (value instanceof List<?> list) {
                text = String.join(",", list.stream().map(String::valueOf).sorted().toList());
            } else if (Boolean.TRUE.equals(value)) {
                text = "1";
            } else {
                text = String.valueOf(value);
            }
            if (!text.isEmpty()) {
                clean.put(key, text);
            }
        });
        return clean;
    }
}
