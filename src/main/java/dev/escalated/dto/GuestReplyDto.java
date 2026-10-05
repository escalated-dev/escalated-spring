package dev.escalated.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.escalated.models.Attachment;
import dev.escalated.models.Reply;
import java.time.Instant;
import java.util.List;

/**
 * A reply as a guest sees it: the message and who wrote it, never the
 * author's email or the ticket it belongs to. Only public replies are ever
 * turned into one; internal notes stay with staff.
 */
public record GuestReplyDto(
        Long id,
        String body,
        @JsonProperty("author_name") String authorName,
        @JsonProperty("author_type") String authorType,
        @JsonProperty("is_agent") boolean isAgent,
        @JsonProperty("created_at") Instant createdAt,
        List<Attachment> attachments) {

    public static GuestReplyDto from(Reply reply) {
        return new GuestReplyDto(
                reply.getId(),
                reply.getBody(),
                reply.getAuthorName(),
                reply.getAuthorType(),
                "agent".equals(reply.getAuthorType()),
                reply.getCreatedAt(),
                List.copyOf(reply.getAttachments()));
    }
}
