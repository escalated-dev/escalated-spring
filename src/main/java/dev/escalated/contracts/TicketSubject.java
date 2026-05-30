package dev.escalated.contracts;

/**
 * Presentation contract for a host-app entity attached to a ticket as a subject
 * (a Project, Customer, asset, …). The Spring package does not own host models;
 * implement this interface on host entities and resolve them via
 * {@link dev.escalated.services.TicketSubjectResolver}.
 */
public interface TicketSubject {

    String ticketSubjectTitle();

    String ticketSubjectSubtitle();

    String ticketSubjectUrl();

    String ticketSubjectColor();

    String ticketSubjectIcon();
}
