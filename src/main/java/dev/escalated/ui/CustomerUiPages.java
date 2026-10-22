package dev.escalated.ui;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.Attachment;
import dev.escalated.models.Reply;
import dev.escalated.models.SatisfactionRating;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.TicketCustomFieldValues;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer pages' props, as escalated-laravel's {@code Customer\TicketController} and
 * {@code CustomerTicketPayload::customerWeb} build them.
 *
 * <p>The customer is the signed-in principal, matched to a ticket's requester email without regard
 * to case. Someone else's ticket is a 404. Public replies only; staff appear by display name, with
 * no email, id or internal notes.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class CustomerUiPages {

    /** The filters the customer list honours, as Laravel's {@code $filterKeys}. */
    static final Set<String> FILTERS = Set.of("status", "priority", "ticket_type", "search", "tag",
            "sort_by", "sort_dir", "per_page", "page");

    private final TicketRepository tickets;
    private final AgentUiPages agentPages;
    private final UiRoutes routes;

    private final TicketCustomFieldValues customFields;

    public CustomerUiPages(TicketRepository tickets, AgentUiPages agentPages, UiRoutes routes,
                           TicketCustomFieldValues customFields) {
        this.customFields = customFields;
        this.tickets = tickets;
        this.agentPages = agentPages;
        this.routes = routes;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> index(String email, Map<String, String> query) {
        Map<String, String> filters = new LinkedHashMap<>();
        query.forEach((key, value) -> {
            if (FILTERS.contains(key)) {
                filters.put(key, value);
            }
        });
        Specification<Ticket> mine = (root, q, cb) ->
                cb.equal(cb.lower(root.get("requesterEmail")), email.toLowerCase(Locale.ROOT));
        Page<Ticket> page = tickets.findAll(mine.and(UiTicketFilters.specification(filters, null)),
                UiTicketFilters.pageable(filters));
        List<Map<String, Object>> rows = new ArrayList<>();
        page.getContent().forEach(ticket -> rows.add(row(ticket)));

        Map<String, Object> visible = new LinkedHashMap<>(filters);
        visible.remove("page");
        visible.remove("per_page");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("tickets", UiPresenter.paginator(page, rows, routes.url("escalated.customer.tickets.index")));
        props.put("filters", visible);
        return props;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> create() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("departments", agentPages.activeDepartments());
        props.put("priorities", Arrays.stream(TicketPriority.values()).map(UiPresenter::priority).toList());
        props.put("custom_fields", customFields.definitions());
        return props;
    }

    /** The customer's own ticket, or a 404. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Ticket own(String reference, String email) {
        Ticket ticket = agentPages.resolve(reference);
        if (ticket.getRequesterEmail() == null || !ticket.getRequesterEmail().equalsIgnoreCase(email.trim())) {
            throw new EntityNotFoundException("Ticket not found");
        }
        return ticket;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> show(String reference, String email) {
        Ticket ticket = tickets.findById(own(reference, email).getId())
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("ticket", payload(ticket));
        props.put("custom_fields", customFields.forTicket(ticket.getId()));
        return props;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public boolean rated(Long ticketId) {
        return tickets.findById(ticketId).map(UiPresenter::submittedRating).isPresent();
    }

    /** A list row with nothing about staff but the assignee's display name. */
    static Map<String, Object> row(Ticket ticket) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", ticket.getId());
        row.put("reference", ticket.getTicketNumber());
        row.put("subject", ticket.getSubject());
        row.put("status", UiPresenter.status(ticket.getStatus()));
        row.put("priority", UiPresenter.priority(ticket.getPriority()));
        row.put("ticket_type", ticket.getTicketType());
        row.put("channel", ticket.getChannel());
        row.put("requester", requester(ticket));
        row.put("department", ticket.getDepartment() == null ? null : Map.of("name", ticket.getDepartment().getName()));
        row.put("assignee", ticket.getAssignedAgent() == null ? null : staff(ticket.getAssignedAgent().getName()));
        row.put("created_at", UiPresenter.iso(ticket.getCreatedAt()));
        row.put("updated_at", UiPresenter.iso(ticket.getUpdatedAt()));
        return row;
    }

    /** {@code CustomerTicketPayload::customerWeb}. */
    static Map<String, Object> payload(Ticket ticket) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reference", ticket.getTicketNumber());
        payload.put("subject", ticket.getSubject());
        payload.put("description", ticket.getBody() == null ? "" : ticket.getBody());
        payload.put("status", UiPresenter.status(ticket.getStatus()));
        payload.put("priority", UiPresenter.priority(ticket.getPriority()));
        payload.put("channel", ticket.getChannel());
        payload.put("department", ticket.getDepartment() == null ? null : Map.of("name", ticket.getDepartment().getName()));

        List<Map<String, Object>> attachments = new ArrayList<>();
        for (Attachment attachment : ticket.getAttachments()) {
            if (attachment.getReply() == null) {
                attachments.add(attachment(attachment));
            }
        }
        payload.put("attachments", attachments);

        List<Reply> replies = new ArrayList<>(ticket.getReplies());
        replies.removeIf(Reply::isInternal);
        replies.sort(Comparator.comparing(Reply::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        List<Map<String, Object>> thread = new ArrayList<>();
        for (Reply reply : replies) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", reply.getId());
            map.put("body", reply.getBody());
            map.put("is_internal_note", false);
            map.put("is_pinned", false);
            boolean requester = "customer".equals(reply.getAuthorType())
                    || (reply.getAuthorEmail() != null && reply.getAuthorEmail().equalsIgnoreCase(ticket.getRequesterEmail()));
            map.put("author", Map.of("name", requester ? String.valueOf(ticket.getRequesterName())
                    : reply.getAuthorName() == null || reply.getAuthorName().isBlank() ? "Support" : reply.getAuthorName()));
            List<Map<String, Object>> files = new ArrayList<>();
            reply.getAttachments().forEach(attachment -> files.add(attachment(attachment)));
            map.put("attachments", files);
            map.put("created_at", UiPresenter.iso(reply.getCreatedAt()));
            thread.add(map);
        }
        payload.put("replies", thread);

        SatisfactionRating rating = UiPresenter.submittedRating(ticket);
        payload.put("satisfaction_rating", rating == null ? null
                : new LinkedHashMap<>(Map.of("rating", rating.getRating(), "comment", String.valueOf(
                        rating.getComment() == null ? "" : rating.getComment()))));
        payload.put("resolved_at", UiPresenter.iso(ticket.getResolvedAt()));
        payload.put("closed_at", UiPresenter.iso(ticket.getClosedAt()));
        payload.put("created_at", UiPresenter.iso(ticket.getCreatedAt()));
        payload.put("updated_at", UiPresenter.iso(ticket.getUpdatedAt()));
        return payload;
    }

    private static Map<String, Object> attachment(Attachment attachment) {
        return UiPresenter.attachment(attachment);
    }

    private static Map<String, Object> requester(Ticket ticket) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", ticket.getRequesterName());
        map.put("email", ticket.getRequesterEmail());
        return map;
    }

    private static Map<String, Object> staff(String name) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", 0);
        map.put("name", name == null || name.isBlank() ? "Support" : name);
        map.put("email", "");
        return map;
    }
}
