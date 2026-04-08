package dev.escalated.events;

import dev.escalated.models.Reply;
import org.springframework.context.ApplicationEvent;

public class ReplyEvent extends ApplicationEvent {

    public enum Type {
        CREATED, UPDATED, DELETED
    }

    private final Reply reply;
    private final Type type;
    private final String actorEmail;

    public ReplyEvent(Object source, Reply reply, Type type, String actorEmail) {
        super(source);
        this.reply = reply;
        this.type = type;
        this.actorEmail = actorEmail;
    }

    public Reply getReply() {
        return reply;
    }

    public Type getType() {
        return type;
    }

    public String getActorEmail() {
        return actorEmail;
    }
}
