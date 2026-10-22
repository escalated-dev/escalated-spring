package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.CustomTicketStatus;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.CustomTicketStatusRepository;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom ticket statuses and the built-in statuses they hang off.
 *
 * <p>A custom status belongs to one of five categories, and lifecycle logic keys off the category:
 * a ticket given a custom status takes its category's built-in anchor ({@link #anchorOf}) as its
 * status. The built-in {@code status} column stays the source of truth, so nothing here depends on
 * the system rows existing.
 */
@Service
public class TicketStatuses {

    /** escalated-laravel's categories, in its order. */
    public static final List<String> CATEGORIES = List.of("new", "open", "pending", "on_hold", "solved");

    /** Values a status input may name that are not a built-in constant but mean one. */
    private static final Map<String, TicketStatus> BUILT_IN_ALIASES = Map.of(
            "waiting_on_customer", TicketStatus.PENDING,
            "waiting_on_agent", TicketStatus.PENDING);

    /** The system rows: one per built-in status, as the pages label and colour them. */
    private static final Map<TicketStatus, String[]> SYSTEM_ROWS = systemRows();

    private final CustomTicketStatusRepository statuses;

    public TicketStatuses(CustomTicketStatusRepository statuses) {
        this.statuses = statuses;
    }

    /** The category a built-in status belongs to. */
    public static String categoryOf(TicketStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING -> "pending";
            case SNOOZED -> "on_hold";
            case RESOLVED, CLOSED, MERGED -> "solved";
            default -> "open";
        };
    }

    /** The built-in status a ticket takes when it gets a custom status of {@code category}. */
    public static TicketStatus anchorOf(String category) {
        return switch (category == null ? "" : category) {
            case "pending" -> TicketStatus.PENDING;
            case "on_hold" -> TicketStatus.IN_PROGRESS;
            case "solved" -> TicketStatus.RESOLVED;
            default -> TicketStatus.OPEN;
        };
    }

    /** The slug of a built-in status (and of its system row): the lower-cased constant. */
    public static String slugOf(TicketStatus status) {
        return status == null ? null : status.name().toLowerCase(Locale.ROOT);
    }

    /** The category of a ticket's effective status: its custom status's, else its built-in one's. */
    public static String categoryOf(Ticket ticket) {
        CustomTicketStatus custom = ticket.getCustomStatus();
        return custom != null ? custom.getCategory() : categoryOf(ticket.getStatus());
    }

    /**
     * A built-in status as an input names it: the constant in any case, or the shared frontend's
     * {@code waiting_on_customer} / {@code waiting_on_agent}, which this port keeps as pending.
     * Null for anything else.
     */
    public static TicketStatus builtIn(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalised = value.trim().toLowerCase(Locale.ROOT);
        TicketStatus alias = BUILT_IN_ALIASES.get(normalised);
        if (alias != null) {
            return alias;
        }
        try {
            return TicketStatus.valueOf(normalised.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** The custom (non-system) status with this slug. A system row's slug is a built-in value. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Optional<CustomTicketStatus> custom(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return statuses.findBySlug(slug.trim()).filter(status -> !status.isSystem());
    }

    /** Whether {@code value} names a built-in status or a custom status slug. */
    public boolean isKnown(String value) {
        return builtIn(value) != null || custom(value).isPresent();
    }

    /** Inserts the system row of every built-in status that has none. Safe to call repeatedly. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void ensureSystemStatuses() {
        for (Map.Entry<TicketStatus, String[]> row : SYSTEM_ROWS.entrySet()) {
            String slug = slugOf(row.getKey());
            if (statuses.existsBySlug(slug)) {
                continue;
            }
            CustomTicketStatus status = new CustomTicketStatus();
            status.setSlug(slug);
            status.setLabel(row.getValue()[0]);
            status.setColor(row.getValue()[1]);
            status.setPosition(Integer.parseInt(row.getValue()[2]));
            status.setCategory(categoryOf(row.getKey()));
            status.setDefaultStatus(row.getKey() == TicketStatus.OPEN);
            status.setSystem(true);
            statuses.save(status);
        }
    }

    /**
     * A free slug for {@code label}: Laravel's {@code Str::slug($label, '_')}, then {@code _2},
     * {@code _3}... past the slugs in use and the built-in values, which belong to the system rows.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public String uniqueSlug(String label) {
        String base = slug(label);
        String candidate = base;
        for (int suffix = 2; reserved(candidate) || statuses.existsBySlug(candidate); suffix++) {
            candidate = base + "_" + suffix;
        }
        return candidate;
    }

    static String slug(String label) {
        String ascii = Normalizer.normalize(label == null ? "" : label, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "status" : slug;
    }

    private static boolean reserved(String slug) {
        // A status input naming one of these means the built-in status, never a custom one.
        return builtIn(slug) != null;
    }

    private static Map<TicketStatus, String[]> systemRows() {
        Map<TicketStatus, String[]> rows = new LinkedHashMap<>();
        rows.put(TicketStatus.OPEN, new String[] {"Open", "#3b82f6", "0"});
        rows.put(TicketStatus.IN_PROGRESS, new String[] {"In Progress", "#8b5cf6", "1"});
        rows.put(TicketStatus.PENDING, new String[] {"Pending", "#f59e0b", "0"});
        rows.put(TicketStatus.SNOOZED, new String[] {"Snoozed", "#64748b", "0"});
        rows.put(TicketStatus.RESOLVED, new String[] {"Resolved", "#10b981", "0"});
        rows.put(TicketStatus.CLOSED, new String[] {"Closed", "#6b7280", "1"});
        rows.put(TicketStatus.MERGED, new String[] {"Merged", "#9ca3af", "2"});
        return rows;
    }
}
