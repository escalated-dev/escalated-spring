package dev.escalated.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO returned by ticket detail (show) endpoints. Wraps the Ticket entity
 * and adds computed fields that the frontend expects on detail views but
 * that should not be present on list responses.
 */
public class TicketDetailDto {

    @JsonUnwrapped
    private final Ticket ticket;

    @JsonProperty("chat_session_id")
    private final Long chatSessionId;

    @JsonProperty("chat_started_at")
    private final Instant chatStartedAt;

    @JsonProperty("chat_messages")
    private final List<Reply> chatMessages;

    @JsonProperty("chat_metadata")
    private final Map<String, Object> chatMetadata;

    @JsonProperty("requester_ticket_count")
    private final long requesterTicketCount;

    @JsonProperty("related_tickets")
    private final List<RelatedTicketDto> relatedTickets;

    public TicketDetailDto(Ticket ticket,
                           Long chatSessionId,
                           Instant chatStartedAt,
                           List<Reply> chatMessages,
                           Map<String, Object> chatMetadata,
                           long requesterTicketCount,
                           List<RelatedTicketDto> relatedTickets) {
        this.ticket = ticket;
        this.chatSessionId = chatSessionId;
        this.chatStartedAt = chatStartedAt;
        this.chatMessages = chatMessages;
        this.chatMetadata = chatMetadata;
        this.requesterTicketCount = requesterTicketCount;
        this.relatedTickets = relatedTickets;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public Long getChatSessionId() {
        return chatSessionId;
    }

    public Instant getChatStartedAt() {
        return chatStartedAt;
    }

    public List<Reply> getChatMessages() {
        return chatMessages;
    }

    public Map<String, Object> getChatMetadata() {
        return chatMetadata;
    }

    public long getRequesterTicketCount() {
        return requesterTicketCount;
    }

    public List<RelatedTicketDto> getRelatedTickets() {
        return relatedTickets;
    }

    /**
     * Lightweight summary of a linked ticket: just the reference number,
     * subject, and current status.
     */
    public static class RelatedTicketDto {

        @JsonProperty("id")
        private final Long id;

        @JsonProperty("ticket_number")
        private final String ticketNumber;

        @JsonProperty("subject")
        private final String subject;

        @JsonProperty("status")
        private final String status;

        @JsonProperty("link_type")
        private final String linkType;

        public RelatedTicketDto(Long id, String ticketNumber, String subject, String status, String linkType) {
            this.id = id;
            this.ticketNumber = ticketNumber;
            this.subject = subject;
            this.status = status;
            this.linkType = linkType;
        }

        public Long getId() {
            return id;
        }

        public String getTicketNumber() {
            return ticketNumber;
        }

        public String getSubject() {
            return subject;
        }

        public String getStatus() {
            return status;
        }

        public String getLinkType() {
            return linkType;
        }
    }
}
