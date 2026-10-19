package dev.escalated.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * A ticket in the signed-in customer's own list. The customer's counterpart
 * of a staff list row: no internal notes, activity log, assigned agent, SLA
 * data, tags, guest token or metadata. {@code last_reply_at} and
 * {@code last_reply_author} consider public replies only, so an internal note
 * never shows through them.
 */
public record CustomerTicketSummaryDto(
        Long id,
        String reference,
        String subject,
        TicketStatus status,
        TicketPriority priority,
        String channel,
        Map<String, String> department,
        @JsonProperty("requester_name") String requesterName,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("resolved_at") Instant resolvedAt,
        @JsonProperty("closed_at") Instant closedAt,
        @JsonProperty("last_reply_at") Instant lastReplyAt,
        @JsonProperty("last_reply_author") String lastReplyAuthor) {

    public static CustomerTicketSummaryDto from(Ticket ticket) {
        Optional<Reply> lastPublic = ticket.getReplies().stream()
                .filter(reply -> !reply.isInternal() && reply.getCreatedAt() != null)
                .max(Comparator.comparing(Reply::getCreatedAt));
        return new CustomerTicketSummaryDto(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getSubject(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getChannel(),
                ticket.getDepartment() == null
                        ? null
                        : Collections.singletonMap("name", ticket.getDepartment().getName()),
                ticket.getRequesterName(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                lastPublic.map(Reply::getCreatedAt).orElse(null),
                lastPublic.map(Reply::getAuthorName).orElse(null));
    }
}
