package dev.escalated.ui;

import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketFollower;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * The ticket queue's filters, as Laravel's {@code TicketService::list} reads them from the query
 * string: {@code status}, {@code priority}, {@code assigned_to}, {@code unassigned},
 * {@code department_id}, {@code ticket_type}, {@code search}, {@code sla_breached}, {@code tag} /
 * {@code tag_ids}, {@code following}, {@code sort_by}, {@code sort_dir}, {@code per_page} and
 * {@code page}. A value that does not parse is ignored, as a filter the list does not know.
 */
public final class UiTicketFilters {

    private static final List<TicketStatus> CLOSED =
            List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED, TicketStatus.MERGED);

    private UiTicketFilters() {}

    public static Specification<Ticket> specification(Map<String, String> filters, Long currentAgentId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            TicketStatus status = parseStatus(filters.get("status"));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            TicketPriority priority = parsePriority(filters.get("priority"));
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            Long assignedTo = parseLong(filters.get("assigned_to"));
            if (assignedTo != null) {
                predicates.add(cb.equal(root.get("assignedAgent").get("id"), assignedTo));
            }
            if (truthy(filters.get("unassigned"))) {
                predicates.add(cb.isNull(root.get("assignedAgent")));
            }
            Long departmentId = parseLong(filters.get("department_id"));
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            String ticketType = blankToNull(filters.get("ticket_type"));
            if (ticketType != null) {
                predicates.add(cb.equal(root.get("ticketType"), ticketType));
            }
            String search = blankToNull(filters.get("search"));
            if (search != null) {
                String pattern = "%" + search.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("subject")), pattern, '\\'),
                        cb.like(cb.lower(root.get("ticketNumber")), pattern, '\\'),
                        cb.like(cb.lower(root.get("requesterEmail")), pattern, '\\'),
                        cb.like(cb.lower(root.get("requesterName")), pattern, '\\')));
            }
            if (truthy(filters.get("sla_breached"))) {
                Instant now = Instant.now();
                Path<Instant> firstDue = root.get("slaFirstResponseDueAt");
                Path<Instant> resolutionDue = root.get("slaDueAt");
                predicates.add(root.get("status").in(CLOSED).not());
                predicates.add(cb.or(
                        cb.and(cb.isNull(root.get("firstRespondedAt")), cb.lessThan(firstDue, now)),
                        cb.lessThan(resolutionDue, now)));
            }
            List<Long> tagIds = new ArrayList<>();
            Long tag = parseLong(filters.get("tag"));
            if (tag != null) {
                tagIds.add(tag);
            }
            String tagList = filters.get("tag_ids");
            if (tagList != null) {
                for (String part : tagList.split(",")) {
                    Long id = parseLong(part);
                    if (id != null) {
                        tagIds.add(id);
                    }
                }
            }
            if (!tagIds.isEmpty()) {
                Subquery<Long> tagged = query.subquery(Long.class);
                Root<Ticket> inner = tagged.from(Ticket.class);
                Join<Ticket, Tag> tags = inner.join("tags");
                tagged.select(inner.get("id")).where(tags.get("id").in(tagIds));
                predicates.add(root.get("id").in(tagged));
            }
            if (truthy(filters.get("following")) && currentAgentId != null) {
                Subquery<Long> followed = query.subquery(Long.class);
                Root<TicketFollower> follower = followed.from(TicketFollower.class);
                followed.select(follower.get("ticketId"))
                        .where(cb.equal(follower.get("userId"), String.valueOf(currentAgentId)));
                predicates.add(root.get("id").in(followed));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Pageable pageable(Map<String, String> filters) {
        int perPage = clamp(parseLong(filters.get("per_page")), 25, 1, 100);
        int page = clamp(parseLong(filters.get("page")), 1, 1, Integer.MAX_VALUE) - 1;
        String sortBy = switch (String.valueOf(filters.get("sort_by"))) {
            case "updated_at" -> "updatedAt";
            case "priority" -> "priority";
            case "status" -> "status";
            case "subject" -> "subject";
            case "reference" -> "ticketNumber";
            default -> "createdAt";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(filters.get("sort_dir"))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, perPage, Sort.by(direction, sortBy).and(Sort.by(Sort.Direction.DESC, "id")));
    }

    /**
     * A status as the pages send it. The lower-cased constants, plus the shared frontend's
     * {@code waiting_on_customer} and {@code waiting_on_agent}, which this port keeps as
     * {@code pending}. Anything else is null.
     */
    public static TicketStatus parseStatus(String value) {
        return dev.escalated.services.TicketStatuses.builtIn(value);
    }

    public static TicketPriority parsePriority(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TicketPriority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static boolean truthy(String value) {
        return value != null && ("1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clamp(Long value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        return (int) Math.max(min, Math.min(max, value));
    }
}
