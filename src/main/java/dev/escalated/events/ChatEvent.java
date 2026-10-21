package dev.escalated.events;

import dev.escalated.models.ChatSession;
import org.springframework.context.ApplicationEvent;

/**
 * A live-chat lifecycle event, announced to webhooks and workflows as
 * {@code chat.started}, {@code chat.assigned}, {@code chat.message} or
 * {@code chat.ended}. Carries ids only: never the visitor's widget token.
 */
public class ChatEvent extends ApplicationEvent {

    public enum Type {
        STARTED(EventNames.CHAT_STARTED),
        ASSIGNED(EventNames.CHAT_ASSIGNED),
        MESSAGE(EventNames.CHAT_MESSAGE),
        ENDED(EventNames.CHAT_ENDED);

        private final String eventName;

        Type(String eventName) {
            this.eventName = eventName;
        }

        public String eventName() {
            return eventName;
        }
    }

    private final Type type;
    private final Long sessionId;
    private final Long ticketId;
    private final Long agentId;

    public ChatEvent(Object source, Type type, ChatSession session) {
        super(source);
        this.type = type;
        this.sessionId = session.getId();
        this.ticketId = session.getTicketId();
        this.agentId = session.getAgentId();
    }

    public Type getType() {
        return type;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Long getAgentId() {
        return agentId;
    }
}
