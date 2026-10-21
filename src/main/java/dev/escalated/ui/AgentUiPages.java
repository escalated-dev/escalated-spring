package dev.escalated.ui;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.ChatSession;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketFollower;
import dev.escalated.models.TicketLink;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.CannedResponseRepository;
import dev.escalated.repositories.ChatSessionRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.MacroRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketFollowerRepository;
import dev.escalated.repositories.TicketLinkRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.TicketActionRegistry;
import dev.escalated.services.TicketSubjectService;
import jakarta.persistence.EntityNotFoundException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The agent pages' props, as escalated-laravel's {@code Agent\DashboardController} and
 * {@code Agent\TicketController} build them. Reads run through the ticket repository, so the
 * department and account visibility rules apply to every list and count.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class AgentUiPages {

    private static final List<TicketStatus> NOT_OPEN = List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED, TicketStatus.MERGED);
    private static final int ACTIVITY_LIMIT = 20;

    private final TicketRepository tickets;
    private final DepartmentRepository departments;
    private final TagRepository tags;
    private final CannedResponseRepository cannedResponses;
    private final MacroRepository macros;
    private final TicketFollowerRepository followers;
    private final ChatSessionRepository chatSessions;
    private final ReplyRepository replies;
    private final TicketLinkRepository links;
    private final TicketSubjectService subjects;
    private final TicketActionRegistry actions;
    private final UiRoutes routes;

    public AgentUiPages(
            TicketRepository tickets,
            DepartmentRepository departments,
            TagRepository tags,
            CannedResponseRepository cannedResponses,
            MacroRepository macros,
            TicketFollowerRepository followers,
            ChatSessionRepository chatSessions,
            ReplyRepository replies,
            TicketLinkRepository links,
            TicketSubjectService subjects,
            TicketActionRegistry actions,
            UiRoutes routes) {
        this.tickets = tickets;
        this.departments = departments;
        this.tags = tags;
        this.cannedResponses = cannedResponses;
        this.macros = macros;
        this.followers = followers;
        this.chatSessions = chatSessions;
        this.replies = replies;
        this.links = links;
        this.subjects = subjects;
        this.actions = actions;
        this.routes = routes;
    }

    /** The ticket a path names: its reference, or its id. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Ticket resolve(String reference) {
        return tickets.findByTicketNumber(reference)
                .or(() -> {
                    Long id = UiTicketFilters.parseLong(reference);
                    return id == null ? java.util.Optional.empty() : tickets.findById(id);
                })
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> dashboard(Long agentId) {
        ZoneId zone = ZoneId.systemDefault();
        Instant startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant startOfWeek = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zone).toInstant();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("open", tickets.count(open()));
        stats.put("my_assigned", agentId == null ? 0L : tickets.count(open().and(assignedTo(agentId))));
        stats.put("unassigned", tickets.count(open().and(unassigned())));
        stats.put("sla_breached", tickets.count(open().and(breached())));
        stats.put("resolved_today", tickets.count(resolvedSince(startOfDay)));

        Map<String, Object> needsAttention = new LinkedHashMap<>();
        needsAttention.put("sla_breaching", rows(tickets.findAll(open().and(breached()), firstFive())));
        needsAttention.put("unassigned_urgent", rows(tickets.findAll(
                open().and(unassigned()).and((root, query, cb) -> cb.equal(root.get("priority"), TicketPriority.URGENT)),
                firstFive())));

        Map<String, Object> performance = new LinkedHashMap<>();
        performance.put("resolved_this_week", agentId == null ? 0L
                : tickets.count(assignedTo(agentId).and(resolvedSince(startOfWeek))));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stats", stats);
        props.put("recentTickets", rows(tickets.findAll(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id"))).getContent()));
        props.put("needsAttention", needsAttention);
        props.put("myPerformance", performance);
        return props;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> index(Map<String, String> filters, Long agentId) {
        return index(filters, agentId, "escalated.agent.tickets.index");
    }

    /** The queue's props, paginated against the named index route. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> index(Map<String, String> filters, Long agentId, String indexRoute) {
        Page<Ticket> page = tickets.findAll(UiTicketFilters.specification(filters, agentId),
                UiTicketFilters.pageable(filters));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("tickets", UiPresenter.paginator(page, rows(page.getContent()),
                routes.url(indexRoute)));
        props.put("filters", new LinkedHashMap<>(filters));
        props.put("departments", activeDepartments());
        props.put("tags", UiPresenter.tags(tags.findAll()));
        return props;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> show(Long ticketId, Long agentId) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
        Map<String, Object> detail = UiPresenter.ticketDetail(ticket, ACTIVITY_LIMIT);
        chat(ticket, detail);
        detail.put("requester_ticket_count", tickets.countByRequesterEmail(ticket.getRequesterEmail()));
        detail.put("related_tickets", related(ticket));
        detail.put("subjects", subjects.serializeForTicket(ticket));

        List<Map<String, Object>> canned = new ArrayList<>();
        List<Map<String, Object>> macroRows = new ArrayList<>();
        if (agentId != null) {
            cannedResponses.findAccessibleByAgent(agentId).forEach(c -> canned.add(UiPresenter.cannedResponse(c)));
            macros.findAccessibleByAgent(agentId).forEach(m -> macroRows.add(UiPresenter.macro(m)));
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("ticket", detail);
        props.put("departments", activeDepartments());
        props.put("tags", UiPresenter.tags(tags.findAll()));
        props.put("cannedResponses", canned);
        props.put("macros", macroRows);
        props.put("is_following", agentId != null
                && followers.existsByTicketIdAndUserId(ticket.getId(), String.valueOf(agentId)));
        props.put("followers_count", followers.findByTicketId(ticket.getId()).size());
        props.put("customActions", customActions(ticket));
        return props;
    }

    /** Follows the ticket, or stops following it. True when the agent now follows it. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public boolean toggleFollow(Long ticketId, Long agentId) {
        String userId = String.valueOf(agentId);
        List<TicketFollower> mine = followers.findByTicketId(ticketId).stream()
                .filter(follower -> userId.equals(follower.getUserId()))
                .toList();
        if (!mine.isEmpty()) {
            followers.deleteAll(mine);
            return false;
        }
        followers.save(new TicketFollower(ticketId, userId));
        return true;
    }

    /** The ids of the ticket's tags. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Long> tagIds(Long ticketId) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
        return ticket.getTags().stream().map(dev.escalated.models.Tag::getId).toList();
    }

    /** Whether the agent may apply this macro: an active macro, shared or theirs. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public boolean macroAvailable(Long macroId, Long agentId) {
        return agentId != null && macros.findAccessibleByAgent(agentId).stream()
                .anyMatch(macro -> macro.getId().equals(macroId));
    }

    private List<Map<String, Object>> customActions(Ticket ticket) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> action : actions.forTicket()) {
            Map<String, Object> row = new LinkedHashMap<>(action);
            row.put("url", routes.url("escalated.agent.tickets.custom-action", ticket.getTicketNumber(), action.get("key")));
            row.put("method", "post");
            list.add(row);
        }
        return list;
    }

    private void chat(Ticket ticket, Map<String, Object> detail) {
        detail.put("chat_session_id", null);
        detail.put("chat_started_at", null);
        detail.put("chat_messages", List.of());
        detail.put("chat_metadata", null);
        if (!"chat".equals(ticket.getChannel())) {
            return;
        }
        ChatSession session = chatSessions.findByTicketId(ticket.getId()).orElse(null);
        if (session == null) {
            return;
        }
        detail.put("chat_session_id", session.getId());
        detail.put("chat_started_at", UiPresenter.iso(session.getCreatedAt()));
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Reply reply : replies.findByTicketIdOrderByCreatedAtAsc(ticket.getId())) {
            messages.add(UiPresenter.reply(reply, true));
        }
        detail.put("chat_messages", messages);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("visitor_name", session.getVisitorName());
        metadata.put("visitor_email", session.getVisitorEmail());
        metadata.put("status", session.getStatus());
        metadata.put("accepted_at", UiPresenter.iso(session.getAcceptedAt()));
        metadata.put("ended_at", UiPresenter.iso(session.getEndedAt()));
        detail.put("chat_metadata", metadata);
    }

    private List<Map<String, Object>> related(Ticket ticket) {
        List<Map<String, Object>> related = new ArrayList<>();
        for (TicketLink link : links.findByTicketId(ticket.getId())) {
            Ticket other = link.getSourceTicket().getId().equals(ticket.getId())
                    ? link.getTargetTicket() : link.getSourceTicket();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", other.getId());
            row.put("reference", other.getTicketNumber());
            row.put("subject", other.getSubject());
            row.put("status", UiPresenter.status(other.getStatus()));
            row.put("link_type", link.getLinkType());
            related.add(row);
        }
        return related;
    }

    List<Map<String, Object>> activeDepartments() {
        List<Map<String, Object>> list = new ArrayList<>();
        departments.findByActiveTrueOrderBySortOrderAsc().forEach(d -> list.add(UiPresenter.department(d)));
        return list;
    }

    private static List<Map<String, Object>> rows(Iterable<Ticket> page) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Ticket ticket : page) {
            list.add(UiPresenter.ticketRow(ticket));
        }
        return list;
    }

    private static PageRequest firstFive() {
        return PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private static Specification<Ticket> open() {
        return (root, query, cb) -> root.get("status").in(NOT_OPEN).not();
    }

    private static Specification<Ticket> assignedTo(Long agentId) {
        return (root, query, cb) -> cb.equal(root.get("assignedAgent").get("id"), agentId);
    }

    private static Specification<Ticket> unassigned() {
        return (root, query, cb) -> cb.isNull(root.get("assignedAgent"));
    }

    private static Specification<Ticket> resolvedSince(Instant since) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("resolvedAt"), since);
    }

    private static Specification<Ticket> breached() {
        return (root, query, cb) -> {
            Instant now = Instant.now();
            return cb.or(
                    cb.and(cb.isNull(root.get("firstRespondedAt")),
                            cb.lessThan(root.<Instant>get("slaFirstResponseDueAt"), now)),
                    cb.lessThan(root.<Instant>get("slaDueAt"), now));
        };
    }
}
