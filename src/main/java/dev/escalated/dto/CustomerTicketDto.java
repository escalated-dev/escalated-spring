package dev.escalated.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.escalated.models.Attachment;
import dev.escalated.models.SatisfactionRating;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A ticket as the signed-in customer who requested it sees it: the guest view
 * ({@link GuestTicketDto}) plus their satisfaction rating and, for a live
 * chat, its messages. Like the guest view it carries public replies only and
 * nothing staff keep to themselves: no internal notes, activity log, assigned
 * agent, SLA data, tags, linked tickets, side conversations, requester
 * history, chat session metadata or custom actions.
 */
public record CustomerTicketDto(
        Long id,
        String reference,
        String subject,
        String description,
        TicketStatus status,
        TicketPriority priority,
        String channel,
        Map<String, String> department,
        @JsonProperty("requester_name") String requesterName,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("resolved_at") Instant resolvedAt,
        @JsonProperty("closed_at") Instant closedAt,
        List<Attachment> attachments,
        List<GuestReplyDto> replies,
        @JsonProperty("satisfaction_rating") Rating satisfactionRating,
        @JsonProperty("chat_messages") List<GuestReplyDto> chatMessages,
        @JsonProperty("custom_fields") @JsonInclude(JsonInclude.Include.NON_NULL)
        List<Map<String, Object>> customFields) {

    /** The customer's own rating of the ticket. */
    public record Rating(int rating, String comment, @JsonProperty("created_at") Instant createdAt) {
    }

    public static CustomerTicketDto from(Ticket ticket) {
        GuestTicketDto guest = GuestTicketDto.from(ticket);
        Rating rating = ticket.getSatisfactionRatings().stream()
                .max(Comparator.comparing(SatisfactionRating::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(r -> new Rating(r.getRating(), r.getComment(), r.getCreatedAt()))
                .orElse(null);
        return new CustomerTicketDto(
                guest.id(),
                guest.reference(),
                guest.subject(),
                guest.description(),
                guest.status(),
                guest.priority(),
                guest.channel(),
                guest.department(),
                guest.requesterName(),
                guest.createdAt(),
                guest.updatedAt(),
                guest.resolvedAt(),
                guest.closedAt(),
                guest.attachments(),
                guest.replies(),
                rating,
                ticket.isLiveChat() ? guest.replies() : null,
                null);
    }

    /** This view with the ticket's custom fields, each carrying its {@code value}. */
    public CustomerTicketDto withCustomFields(List<Map<String, Object>> fields) {
        return new CustomerTicketDto(id, reference, subject, description, status, priority, channel, department,
                requesterName, createdAt, updatedAt, resolvedAt, closedAt, attachments, replies, satisfactionRating,
                chatMessages, fields);
    }
}
