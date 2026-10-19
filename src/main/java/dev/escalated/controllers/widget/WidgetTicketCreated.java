package dev.escalated.controllers.widget;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.escalated.models.Ticket;

/**
 * The answer to a widget ticket: the ticket's fields as before, plus the
 * top-level keys the shared widget reads.
 */
final class WidgetTicketCreated {

    private final Ticket ticket;

    WidgetTicketCreated(Ticket ticket) {
        this.ticket = ticket;
    }

    @JsonUnwrapped
    public Ticket getTicket() {
        return ticket;
    }

    @JsonProperty("message")
    public String getMessage() {
        return "Ticket created.";
    }

    @JsonProperty("reference")
    public String getReference() {
        return ticket.getTicketNumber();
    }

    @JsonProperty("guest_access_token")
    public String getGuestAccessTokenForWidget() {
        return ticket.getGuestAccessToken();
    }
}
