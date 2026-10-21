package dev.escalated.events;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shared event vocabulary (escalated-developer-context ADR
 * {@code decisions/2026-10-21-event-vocabulary.md}).
 *
 * <p>Webhooks and workflow triggers are announced under the canonical names
 * only. Names stored by earlier releases (a webhook's {@code events}, a
 * workflow's {@code trigger_event}) are read as their canonical name, and are
 * never emitted or offered.
 */
public final class EventNames {

    public static final String TICKET_CREATED = "ticket.created";
    public static final String TICKET_UPDATED = "ticket.updated";
    public static final String TICKET_STATUS_CHANGED = "ticket.status_changed";
    public static final String TICKET_RESOLVED = "ticket.resolved";
    public static final String TICKET_CLOSED = "ticket.closed";
    public static final String TICKET_REOPENED = "ticket.reopened";
    public static final String TICKET_ASSIGNED = "ticket.assigned";
    public static final String TICKET_UNASSIGNED = "ticket.unassigned";
    public static final String TICKET_ESCALATED = "ticket.escalated";
    public static final String TICKET_PRIORITY_CHANGED = "ticket.priority_changed";
    public static final String TICKET_DEPARTMENT_CHANGED = "ticket.department_changed";
    public static final String TICKET_TAG_ADDED = "ticket.tag_added";
    public static final String TICKET_TAG_REMOVED = "ticket.tag_removed";
    public static final String REPLY_CREATED = "reply.created";
    public static final String NOTE_CREATED = "note.created";
    public static final String SLA_WARNING = "sla.warning";
    public static final String SLA_BREACHED = "sla.breached";
    public static final String CHAT_STARTED = "chat.started";
    public static final String CHAT_ASSIGNED = "chat.assigned";
    public static final String CHAT_TRANSFERRED = "chat.transferred";
    public static final String CHAT_MESSAGE = "chat.message";
    public static final String CHAT_ENDED = "chat.ended";

    /** Every canonical name in the ADR. */
    public static final List<String> CANONICAL = List.of(
            TICKET_CREATED, TICKET_UPDATED, TICKET_STATUS_CHANGED, TICKET_RESOLVED, TICKET_CLOSED,
            TICKET_REOPENED, TICKET_ASSIGNED, TICKET_UNASSIGNED, TICKET_ESCALATED, TICKET_PRIORITY_CHANGED,
            TICKET_DEPARTMENT_CHANGED, TICKET_TAG_ADDED, TICKET_TAG_REMOVED, REPLY_CREATED, NOTE_CREATED,
            SLA_WARNING, SLA_BREACHED, CHAT_STARTED, CHAT_ASSIGNED, CHAT_TRANSFERRED, CHAT_MESSAGE, CHAT_ENDED);

    /**
     * The canonical names this package emits, and so offers as workflow
     * triggers. {@code chat.transferred} is canonical but this port has no
     * chat transfer yet.
     */
    public static final List<String> EMITTED = CANONICAL.stream()
            .filter(name -> !CHAT_TRANSFERRED.equals(name))
            .toList();

    /** Legacy name to canonical name. Read, never emitted. */
    public static final Map<String, String> LEGACY;

    static {
        Map<String, String> legacy = new LinkedHashMap<>();
        legacy.put("ticket.replied", REPLY_CREATED);
        legacy.put("internal_note.added", NOTE_CREATED);
        legacy.put("internal_note.created", NOTE_CREATED);
        legacy.put("tag.added", TICKET_TAG_ADDED);
        legacy.put("ticket.tagged", TICKET_TAG_ADDED);
        legacy.put("tag.removed", TICKET_TAG_REMOVED);
        legacy.put("ticket.untagged", TICKET_TAG_REMOVED);
        legacy.put("chat.session_started", CHAT_STARTED);
        legacy.put("chat.session_ended", CHAT_ENDED);
        LEGACY = Map.copyOf(legacy);
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.:*-]+");

    private EventNames() {
    }

    /** The canonical name for {@code name}; an unknown name passes through. */
    public static String canonical(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return LEGACY.getOrDefault(trimmed, trimmed);
    }

    /** {@code name} (read as canonical) plus every legacy name that means it. */
    public static List<String> aliasesOf(String name) {
        String canonical = canonical(name);
        List<String> names = new ArrayList<>();
        names.add(canonical);
        LEGACY.entrySet().stream()
                .filter(entry -> entry.getValue().equals(canonical))
                .map(Map.Entry::getKey)
                .sorted()
                .forEach(names::add);
        return names;
    }

    /** The event names in a stored subscription list, read as canonical names. */
    public static List<String> namesIn(String stored) {
        List<String> names = new ArrayList<>();
        if (stored == null) {
            return names;
        }
        Matcher matcher = NAME.matcher(stored);
        while (matcher.find()) {
            String canonical = canonical(matcher.group());
            if (!names.contains(canonical)) {
                names.add(canonical);
            }
        }
        return names;
    }

    /**
     * A stored subscription list with every legacy name rewritten to its
     * canonical name, keeping whatever separators (commas, a JSON array) the
     * caller used.
     */
    public static String canonicalise(String stored) {
        if (stored == null) {
            return null;
        }
        Matcher matcher = NAME.matcher(stored);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(canonical(matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * The canonical names a ticket event is announced under. A status change
     * to resolved, closed or reopened is announced both as
     * {@code ticket.status_changed} and as the specific transition.
     */
    public static List<String> forTicketEvent(TicketEvent.Type type) {
        return switch (type) {
            case CREATED -> List.of(TICKET_CREATED);
            case UPDATED -> List.of(TICKET_UPDATED);
            case ASSIGNED -> List.of(TICKET_ASSIGNED);
            case UNASSIGNED -> List.of(TICKET_UNASSIGNED);
            case STATUS_CHANGED -> List.of(TICKET_STATUS_CHANGED);
            case RESOLVED -> List.of(TICKET_STATUS_CHANGED, TICKET_RESOLVED);
            case CLOSED -> List.of(TICKET_STATUS_CHANGED, TICKET_CLOSED);
            case REOPENED -> List.of(TICKET_STATUS_CHANGED, TICKET_REOPENED);
            case PRIORITY_CHANGED -> List.of(TICKET_PRIORITY_CHANGED);
            case DEPARTMENT_CHANGED -> List.of(TICKET_DEPARTMENT_CHANGED);
            case TAG_ADDED -> List.of(TICKET_TAG_ADDED);
            case TAG_REMOVED -> List.of(TICKET_TAG_REMOVED);
            case ESCALATED -> List.of(TICKET_ESCALATED);
            case SLA_WARNING -> List.of(SLA_WARNING);
            case SLA_BREACHED -> List.of(SLA_BREACHED);
            // Not in the ADR, but existing subscribers rely on them.
            case MERGED -> List.of("ticket.merged");
            case SPLIT -> List.of("ticket.split");
            case SNOOZED -> List.of("ticket.snoozed");
            case UNSNOOZED -> List.of("ticket.unsnoozed");
            case DELETED -> List.of("ticket.deleted");
        };
    }

    /**
     * Workflow triggers for a ticket event: the ADR names only. Merges,
     * splits, snoozes and deletions are webhook-only.
     */
    public static List<String> workflowTriggersFor(TicketEvent.Type type) {
        return forTicketEvent(type).stream().filter(CANONICAL::contains).toList();
    }

    /** The name a reply event is announced under. */
    public static String forReply(ReplyEvent event) {
        if (event.getType() == ReplyEvent.Type.CREATED) {
            return event.getReply() != null && event.getReply().isInternal() ? NOTE_CREATED : REPLY_CREATED;
        }
        return "reply." + event.getType().name().toLowerCase();
    }
}
