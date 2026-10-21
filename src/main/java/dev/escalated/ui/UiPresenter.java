package dev.escalated.ui;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.Attachment;
import dev.escalated.models.CannedResponse;
import dev.escalated.models.Department;
import dev.escalated.models.Macro;
import dev.escalated.models.Reply;
import dev.escalated.models.SatisfactionRating;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketActivity;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;

/**
 * Entities as the shared pages read them: snake_case keys, as escalated-laravel's models
 * serialise, and only the fields a page uses.
 *
 * <p>Every map is built field by field from an allowlist, never by serialising an entity, so no
 * credential an entity holds (two-factor secrets, guest grants, API token hashes) can reach a
 * page. Call these inside a transaction: they walk lazy associations.
 */
public final class UiPresenter {

    private UiPresenter() {}

    /** A ticket as a list row: what TicketList and the dashboard read. */
    public static Map<String, Object> ticketRow(Ticket ticket) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", ticket.getId());
        row.put("reference", ticket.getTicketNumber());
        row.put("subject", ticket.getSubject());
        row.put("status", status(ticket.getStatus()));
        row.put("priority", priority(ticket.getPriority()));
        row.put("ticket_type", ticket.getTicketType());
        row.put("channel", ticket.getChannel());
        row.put("requester_name", ticket.getRequesterName());
        row.put("requester_email", ticket.getRequesterEmail());
        row.put("requester", requester(ticket));
        row.put("assigned_to", ticket.getAssignedAgent() == null ? null : ticket.getAssignedAgent().getId());
        row.put("assignee", agent(ticket.getAssignedAgent()));
        row.put("department_id", ticket.getDepartment() == null ? null : ticket.getDepartment().getId());
        row.put("department", department(ticket.getDepartment()));
        row.put("tags", tags(ticket.getTags()));
        row.put("first_response_at", iso(ticket.getFirstRespondedAt()));
        row.put("first_response_due_at", iso(ticket.getSlaFirstResponseDueAt()));
        row.put("resolution_due_at", iso(ticket.getSlaDueAt()));
        row.put("sla_first_response_breached", firstResponseBreached(ticket));
        row.put("sla_resolution_breached", resolutionBreached(ticket));
        row.put("snoozed_until", iso(ticket.getSnoozedUntil()));
        row.put("resolved_at", iso(ticket.getResolvedAt()));
        row.put("closed_at", iso(ticket.getClosedAt()));
        row.put("created_at", iso(ticket.getCreatedAt()));
        row.put("updated_at", iso(ticket.getUpdatedAt()));
        Reply latest = latestReply(ticket);
        row.put("latest_reply", latest == null ? null : replySummary(latest));
        row.put("last_reply_at", latest == null ? null : iso(latest.getCreatedAt()));
        row.put("last_reply_author", latest == null ? null : latest.getAuthorName());
        return row;
    }

    /** The agent's view of one ticket: the row plus its conversation and history. */
    public static Map<String, Object> ticketDetail(Ticket ticket, int activityLimit) {
        Map<String, Object> detail = ticketRow(ticket);
        detail.put("description", ticket.getBody());

        List<Reply> replies = new ArrayList<>(ticket.getReplies());
        replies.sort(Comparator.comparing(Reply::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        List<Map<String, Object>> thread = new ArrayList<>();
        for (Reply reply : replies) {
            thread.add(reply(reply, true));
        }
        detail.put("replies", thread);

        List<Map<String, Object>> attachments = new ArrayList<>();
        for (Attachment attachment : ticket.getAttachments()) {
            if (attachment.getReply() == null) {
                attachments.add(attachment(attachment));
            }
        }
        detail.put("attachments", attachments);

        List<TicketActivity> activities = new ArrayList<>(ticket.getActivities());
        activities.sort(Comparator.comparing(TicketActivity::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        List<Map<String, Object>> history = new ArrayList<>();
        for (TicketActivity activity : activities.subList(0, Math.min(activityLimit, activities.size()))) {
            history.add(activity(activity));
        }
        detail.put("activities", history);

        detail.put("sla_policy", ticket.getSlaPolicy() == null ? null
                : Map.of("id", ticket.getSlaPolicy().getId(), "name", String.valueOf(ticket.getSlaPolicy().getName())));
        SatisfactionRating rating = submittedRating(ticket);
        detail.put("satisfaction_rating", rating == null ? null : rating(rating));
        detail.put("pinned_notes", List.of());
        return detail;
    }

    public static Map<String, Object> reply(Reply reply, boolean staff) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", reply.getId());
        map.put("body", reply.getBody());
        map.put("is_internal_note", reply.isInternal());
        map.put("is_pinned", false);
        map.put("type", reply.isInternal() ? "note" : "reply");
        Map<String, Object> author = new LinkedHashMap<>();
        author.put("name", reply.getAuthorName());
        if (staff) {
            author.put("email", reply.getAuthorEmail());
        }
        map.put("author", author);
        map.put("author_type", reply.getAuthorType());
        List<Map<String, Object>> attachments = new ArrayList<>();
        for (Attachment attachment : reply.getAttachments()) {
            attachments.add(attachment(attachment));
        }
        map.put("attachments", attachments);
        map.put("created_at", iso(reply.getCreatedAt()));
        return map;
    }

    public static Map<String, Object> attachment(Attachment attachment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", attachment.getId());
        map.put("original_filename", attachment.getFileName());
        map.put("filename", attachment.getFileName());
        map.put("mime_type", attachment.getMimeType());
        map.put("size", attachment.getFileSize());
        map.put("url", UiRoutes.PREFIX + "/attachments/" + attachment.getId() + "/download");
        return map;
    }

    public static Map<String, Object> activity(TicketActivity activity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", activity.getId());
        map.put("type", activity.getType());
        map.put("description", activity.getDescription());
        Map<String, Object> causer = new LinkedHashMap<>();
        causer.put("name", activity.getActorName() != null ? activity.getActorName() : activity.getActorEmail());
        map.put("causer", activity.getActorName() == null && activity.getActorEmail() == null ? null : causer);
        Map<String, Object> properties = new LinkedHashMap<>();
        if (activity.getOldValue() != null) {
            properties.put("old", activity.getOldValue());
        }
        if (activity.getNewValue() != null) {
            properties.put("new", activity.getNewValue());
        }
        map.put("properties", properties);
        map.put("created_at", iso(activity.getCreatedAt()));
        return map;
    }

    /** The rating the requester gave, if any. A survey sent but not answered holds a 0. */
    public static SatisfactionRating submittedRating(Ticket ticket) {
        SatisfactionRating found = null;
        for (SatisfactionRating rating : ticket.getSatisfactionRatings()) {
            if (rating.getRating() > 0) {
                found = rating;
            }
        }
        return found;
    }

    public static Map<String, Object> rating(SatisfactionRating rating) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rating.getId());
        map.put("rating", rating.getRating());
        map.put("comment", rating.getComment());
        map.put("created_at", iso(rating.getCreatedAt()));
        return map;
    }

    /** An agent as others see them: id, name, email. */
    public static Map<String, Object> agent(AgentProfile agent) {
        if (agent == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", agent.getId());
        map.put("name", agent.getName());
        map.put("email", agent.getEmail());
        return map;
    }

    public static Map<String, Object> department(Department department) {
        if (department == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", department.getId());
        map.put("name", department.getName());
        return map;
    }

    public static Map<String, Object> tag(Tag tag) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tag.getId());
        map.put("name", tag.getName());
        map.put("color", tag.getColor());
        return map;
    }

    public static List<Map<String, Object>> tags(Iterable<Tag> tags) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tag tag : tags) {
            list.add(tag(tag));
        }
        list.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));
        return list;
    }

    public static Map<String, Object> cannedResponse(CannedResponse response) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", response.getId());
        map.put("title", response.getTitle());
        map.put("body", response.getContent());
        map.put("shortcut", response.getShortcut());
        map.put("category", response.getCategory());
        map.put("is_shared", response.isShared());
        return map;
    }

    public static Map<String, Object> macro(Macro macro) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", macro.getId());
        map.put("name", macro.getName());
        map.put("description", macro.getDescription());
        map.put("is_shared", macro.isShared());
        map.put("order", macro.getSortOrder());
        return map;
    }

    /** Laravel's length-aware paginator, as Inertia receives it. */
    public static <T> Map<String, Object> paginator(Page<T> page, List<Map<String, Object>> rows, String path) {
        int current = page.getNumber() + 1;
        int last = Math.max(page.getTotalPages(), 1);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("current_page", current);
        map.put("data", rows);
        map.put("first_page_url", path + "?page=1");
        map.put("from", rows.isEmpty() ? null : page.getNumber() * page.getSize() + 1);
        map.put("last_page", last);
        map.put("last_page_url", path + "?page=" + last);
        List<Map<String, Object>> links = new ArrayList<>();
        links.add(link(current > 1 ? path + "?page=" + (current - 1) : null, "&laquo; Previous", false));
        for (int number = 1; number <= last; number++) {
            links.add(link(path + "?page=" + number, String.valueOf(number), number == current));
        }
        links.add(link(current < last ? path + "?page=" + (current + 1) : null, "Next &raquo;", false));
        map.put("links", links);
        map.put("next_page_url", current < last ? path + "?page=" + (current + 1) : null);
        map.put("path", path);
        map.put("per_page", page.getSize());
        map.put("prev_page_url", current > 1 ? path + "?page=" + (current - 1) : null);
        map.put("to", rows.isEmpty() ? null : page.getNumber() * page.getSize() + rows.size());
        map.put("total", page.getTotalElements());
        return map;
    }

    /** Statuses as the pages name them: the lower-cased enum constant. */
    public static String status(TicketStatus status) {
        return status == null ? null : status.name().toLowerCase(Locale.ROOT);
    }

    public static String priority(TicketPriority priority) {
        return priority == null ? null : priority.name().toLowerCase(Locale.ROOT);
    }

    public static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    public static boolean firstResponseBreached(Ticket ticket) {
        Instant due = ticket.getSlaFirstResponseDueAt();
        if (due == null) {
            return false;
        }
        Instant responded = ticket.getFirstRespondedAt();
        return responded != null ? responded.isAfter(due) : Instant.now().isAfter(due) && isOpen(ticket);
    }

    public static boolean resolutionBreached(Ticket ticket) {
        Instant due = ticket.getSlaDueAt();
        if (due == null) {
            return false;
        }
        Instant resolved = ticket.getResolvedAt();
        return resolved != null ? resolved.isAfter(due) : Instant.now().isAfter(due) && isOpen(ticket);
    }

    public static boolean isOpen(Ticket ticket) {
        return !Set.of(TicketStatus.RESOLVED, TicketStatus.CLOSED, TicketStatus.MERGED).contains(ticket.getStatus());
    }

    private static Map<String, Object> requester(Ticket ticket) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", ticket.getRequesterName());
        map.put("email", ticket.getRequesterEmail());
        return map;
    }

    private static Reply latestReply(Ticket ticket) {
        return ticket.getReplies().stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(Reply::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static Map<String, Object> replySummary(Reply reply) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", reply.getId());
        map.put("is_internal_note", reply.isInternal());
        map.put("author", Map.of("name", String.valueOf(reply.getAuthorName())));
        map.put("created_at", iso(reply.getCreatedAt()));
        return map;
    }

    private static Map<String, Object> link(String url, String label, boolean active) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("url", url);
        map.put("label", label);
        map.put("active", active);
        return map;
    }
}
