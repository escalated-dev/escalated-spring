package dev.escalated.models;

/**
 * The built-in ticket statuses: escalated-laravel's TicketStatus values (stored as the constant's
 * name), plus this port's {@code SNOOZED} and {@code MERGED}. As in Laravel, an escalation rule's
 * {@code escalate} action sets {@code ESCALATED}, reopening sets {@code REOPENED}, and a live
 * chat's ticket is {@code LIVE}.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_ON_CUSTOMER,
    WAITING_ON_AGENT,
    ESCALATED,
    RESOLVED,
    CLOSED,
    REOPENED,
    LIVE,
    SNOOZED,
    MERGED
}
