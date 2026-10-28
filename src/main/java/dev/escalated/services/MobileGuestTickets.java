package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Attachment;
import dev.escalated.models.Department;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TicketRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The mobile ticket object of the mobile guest API
 * (escalated-developer-context {@code domain-model/mobile-guest-api.md}).
 * Allow-listed: the ticket model is never serialized, internal notes never
 * appear, and staff are named by display name only.
 */
@Service
public class MobileGuestTickets {

    private final TicketRepository tickets;
    private final ReplyRepository replies;

    public MobileGuestTickets(TicketRepository tickets, ReplyRepository replies) {
        this.tickets = tickets;
        this.replies = replies;
    }

    /**
     * The ticket as the guest holding {@code grant} sees it. {@code withReplies}
     * fills {@code replies} (public replies only, newest first); otherwise it is
     * empty, as on create.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> present(Long ticketId, String grant, Instant grantExpiresAt, boolean withReplies) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow();
        String guestName = blank(ticket.getRequesterName()) ? "Guest" : ticket.getRequesterName();
        String guestEmail = ticket.getRequesterEmail();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", ticket.getId());
        data.put("reference", ticket.getTicketNumber());
        data.put("guest_access_token", grant);
        data.put("guest_access_expires_at", grant == null ? null : iso(grantExpiresAt));
        data.put("subject", ticket.getSubject());
        data.put("description", ticket.getBody() == null ? "" : ticket.getBody());
        data.put("status", status(ticket.getStatus()));
        data.put("priority", priority(ticket.getPriority()));
        data.put("channel", ticket.getChannel());
        data.put("metadata", Collections.emptyMap());
        data.put("requester", person(guestName, guestEmail == null ? "" : guestEmail));
        data.put("assignee", assignee(ticket.getAssignedAgent()));
        data.put("department", department(ticket.getDepartment()));
        data.put("tags", List.of());
        data.put("replies", withReplies ? replies(ticket, guestName, guestEmail) : List.of());
        data.put("activities", List.of());
        data.put("sla", sla(ticket));
        data.put("is_following", false);
        data.put("followers_count", 0);
        data.put("resolved_at", iso(ticket.getResolvedAt()));
        data.put("closed_at", iso(ticket.getClosedAt()));
        data.put("created_at", iso(ticket.getCreatedAt()));
        data.put("updated_at", iso(ticket.getUpdatedAt()));
        return data;
    }

    private List<Map<String, Object>> replies(Ticket ticket, String guestName, String guestEmail) {
        List<Reply> visible = new ArrayList<>(replies.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(ticket.getId()));
        Collections.reverse(visible);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Reply reply : visible) {
            if (reply.isInternal()) {
                continue;
            }
            boolean fromGuest = !"agent".equals(reply.getAuthorType())
                    && reply.getAuthorEmail() != null && guestEmail != null
                    && reply.getAuthorEmail().trim().equalsIgnoreCase(guestEmail.trim());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", reply.getId());
            entry.put("body", reply.getBody() == null ? "" : reply.getBody());
            entry.put("is_internal_note", false);
            entry.put("is_pinned", false);
            entry.put("author", fromGuest
                    ? person(guestName, guestEmail)
                    : person(blank(reply.getAuthorName()) ? "Support" : reply.getAuthorName(), ""));
            entry.put("attachments", reply.getAttachments().stream().map(MobileGuestTickets::attachment).toList());
            entry.put("created_at", iso(reply.getCreatedAt()));
            out.add(entry);
        }
        return out;
    }

    private static Map<String, Object> attachment(Attachment attachment) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", attachment.getId());
        entry.put("filename", attachment.getFileName());
        entry.put("mime_type", attachment.getMimeType());
        entry.put("size", attachment.getFileSize());
        // No guest-authorized download route exists in this package yet.
        entry.put("url", null);
        return entry;
    }

    private static Map<String, Object> person(String name, String email) {
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("id", 0);
        person.put("name", name);
        person.put("email", email);
        return person;
    }

    private static Map<String, Object> assignee(AgentProfile agent) {
        if (agent == null) {
            return null;
        }
        return person(blank(agent.getName()) ? "Support" : agent.getName(), "");
    }

    private static Map<String, Object> department(Department department) {
        if (department == null) {
            return null;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", department.getId());
        entry.put("name", department.getName());
        return entry;
    }

    private static Map<String, Object> sla(Ticket ticket) {
        Instant now = Instant.now();
        Instant firstDue = ticket.getSlaFirstResponseDueAt();
        Instant firstAt = ticket.getFirstRespondedAt();
        Instant resolutionDue = ticket.getSlaDueAt();
        Instant resolvedAt = ticket.getResolvedAt();
        Map<String, Object> sla = new LinkedHashMap<>();
        sla.put("first_response_due_at", iso(firstDue));
        sla.put("first_response_at", iso(firstAt));
        sla.put("first_response_breached", firstDue != null && (firstAt == null ? now : firstAt).isAfter(firstDue));
        sla.put("resolution_due_at", iso(resolutionDue));
        sla.put("resolution_breached", resolutionDue != null
                && (resolvedAt == null ? now : resolvedAt).isAfter(resolutionDue));
        return sla;
    }

    static Map<String, Object> status(TicketStatus status) {
        TicketStatus value = status == null ? TicketStatus.OPEN : status;
        return Map.of("value", value.name().toLowerCase(Locale.ROOT), "label", label(value.name()));
    }

    static Map<String, Object> priority(TicketPriority priority) {
        TicketPriority value = priority == null ? TicketPriority.MEDIUM : priority;
        return Map.of("value", value.name().toLowerCase(Locale.ROOT), "label", label(value.name()));
    }

    /** {@code IN_PROGRESS} reads {@code In Progress}. */
    private static String label(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.toLowerCase(Locale.ROOT).split("_")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
