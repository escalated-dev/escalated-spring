package dev.escalated.controllers.widget;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.escalated.models.Ticket;

/**
 * The answer to a widget ticket, in the Laravel reference's shape:
 * {@code message}, {@code reference}, {@code guest_access_token} and
 * {@code expires_at}. Nothing else.
 *
 * <p>It used to unwrap the whole {@code Ticket} entity. The ticket's contact is
 * looked up by email and reused, so an anonymous POST naming someone else's
 * address got back that contact's stored name, host user id and metadata.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
record WidgetTicketCreated(
        @JsonProperty("message") String message,
        @JsonProperty("reference") String reference,
        @JsonProperty("guest_access_token") String guestAccessToken,
        @JsonProperty("expires_at") String expiresAt) {

    static WidgetTicketCreated of(Ticket ticket, dev.escalated.services.GuestAccessGrants.Grant grant) {
        return new WidgetTicketCreated("Ticket created successfully.", ticket.getTicketNumber(), grant.token(),
                grant.expiresAt().toString());
    }
}
