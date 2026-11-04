package dev.escalated.models;

/**
 * The built-in ticket statuses: escalated-laravel's TicketStatus values (stored as the constant's
 * name), plus this port's {@code SNOOZED} and {@code MERGED}. Laravel's {@code escalated},
 * {@code reopened} and {@code live} are not built-in here.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_ON_CUSTOMER,
    WAITING_ON_AGENT,
    RESOLVED,
    CLOSED,
    SNOOZED,
    MERGED
}
