package dev.escalated.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.escalated.models.Attachment;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A ticket as the guest holding its access token sees it: the fields the
 * shared frontend's guest ticket page reads (reference, subject, description,
 * status, priority, department name, attachments, replies) and nothing staff
 * keep to themselves. Internal notes, the activity log, the assigned agent,
 * SLA data, tags, links and side conversations are left out.
 */
public record GuestTicketDto(
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
        List<GuestReplyDto> replies) {

    public static GuestTicketDto from(Ticket ticket) {
        List<GuestReplyDto> publicReplies = ticket.getReplies().stream()
                .filter(reply -> !reply.isInternal())
                .sorted(Comparator.comparing(Reply::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(GuestReplyDto::from)
                .toList();

        // Attachments sent with a reply travel with that reply; only the ones
        // filed on the ticket itself belong here.
        List<Attachment> ticketAttachments = ticket.getAttachments().stream()
                .filter(attachment -> attachment.getReply() == null)
                .toList();

        return new GuestTicketDto(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getSubject(),
                ticket.getBody(),
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
                ticketAttachments,
                publicReplies);
    }
}
