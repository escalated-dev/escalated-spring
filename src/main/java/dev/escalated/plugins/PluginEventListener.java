package dev.escalated.plugins;

import dev.escalated.events.ChatEvent;
import dev.escalated.events.EventNames;
import dev.escalated.events.ReplyEvent;
import dev.escalated.events.TicketEvent;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers the canonical domain events (ADR 2026-10-21) to active plugins' actions, after the change
 * has committed and on another thread, from the same events that fire webhooks and workflows. Legacy
 * names are never delivered, and a failing plugin never reaches the request that caused the event.
 *
 * <p>Payloads, as the plugin contract has them: the ticket flat plus {@code ticket} and the event's
 * extras; the reply flat plus {@code reply} and {@code ticket}; {@code {session}} for {@code chat.*}.
 * Background events act for no user.
 */
@Component
public class PluginEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(PluginEventListener.class);

    private final PluginRegistry registry;

    public PluginEventListener(PluginRegistry registry) {
        this.registry = registry;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onTicketEvent(TicketEvent event) {
        if (!registry.hasPlugins() || event.getTicket() == null || event.getTicket().getId() == null) {
            return;
        }
        try {
            Map<String, Object> ticket = registry.domain().ticketById(event.getTicket().getId());
            if (ticket == null) {
                return;
            }
            for (String name : EventNames.workflowTriggersFor(event.getType())) {
                Map<String, Object> payload = new LinkedHashMap<>(ticket);
                payload.put("ticket", ticket);
                payload.putAll(extras(name, event, ticket));
                registry.dispatchAction(name, payload, null);
            }
        } catch (RuntimeException ex) {
            LOG.warn("Delivering ticket event {} to plugins failed: {}", event.getType(), ex.toString());
        }
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onReplyEvent(ReplyEvent event) {
        if (!registry.hasPlugins() || event.getType() != ReplyEvent.Type.CREATED || event.getReply() == null
                || event.getReply().getId() == null) {
            return;
        }
        try {
            Map<String, Object> found = registry.domain().replyWithTicket(event.getReply().getId());
            if (found == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> reply = (Map<String, Object>) found.get("reply");
            Map<String, Object> payload = new LinkedHashMap<>(reply);
            payload.put("reply", reply);
            payload.put("ticket", found.get("ticket"));
            registry.dispatchAction(EventNames.forReply(event), payload, null);
        } catch (RuntimeException ex) {
            LOG.warn("Delivering reply event to plugins failed: {}", ex.toString());
        }
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onChatEvent(ChatEvent event) {
        if (!registry.hasPlugins()) {
            return;
        }
        try {
            Map<String, Object> session = registry.domain()
                    .chatSession(event.getSessionId(), event.getTicketId(), event.getAgentId());
            if (event.getTransfer() != null) {
                session.put("from_agent_id", event.getTransfer().fromAgentId());
                session.put("to_agent_id", event.getTransfer().toAgentId());
                session.put("to_department_id", event.getTransfer().toDepartmentId());
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("session", session);
            registry.dispatchAction(event.getType().eventName(), payload, null);
        } catch (RuntimeException ex) {
            LOG.warn("Delivering chat event {} to plugins failed: {}", event.getType(), ex.toString());
        }
    }

    /** The event's own keys beside the ticket. */
    private Map<String, Object> extras(String name, TicketEvent event, Map<String, Object> ticket) {
        Map<String, Object> extra = new LinkedHashMap<>();
        switch (name) {
            case EventNames.TICKET_STATUS_CHANGED -> {
                extra.put("old_status", lower(event.getOldValue()));
                extra.put("new_status", lower(event.getNewValue()));
                if (event.hasStatusDetail()) {
                    extra.put("old_custom_status", event.getOldCustomStatus());
                    extra.put("new_custom_status", event.getNewCustomStatus());
                }
            }
            case EventNames.TICKET_ASSIGNED -> extra.put("agent_id", ticket.get("assigned_to"));
            case EventNames.TICKET_PRIORITY_CHANGED -> {
                extra.put("old_priority", lower(event.getOldValue()));
                extra.put("new_priority", lower(event.getNewValue()));
            }
            case EventNames.TICKET_DEPARTMENT_CHANGED -> {
                extra.put("old_department_id", PluginJson.id(event.getOldValue()));
                extra.put("new_department_id", PluginJson.id(event.getNewValue()));
            }
            case EventNames.TICKET_TAG_ADDED, EventNames.TICKET_TAG_REMOVED -> {
                Map<String, Object> tag = registry.domain().tagByName(event.getNewValue());
                if (tag == null) {
                    tag = new LinkedHashMap<>();
                    tag.put("id", null);
                    tag.put("name", event.getNewValue());
                    tag.put("slug", PluginDomain.tagSlug(event.getNewValue()));
                } else {
                    tag.remove("color");
                }
                extra.put("tag", tag);
            }
            case EventNames.SLA_WARNING, EventNames.SLA_BREACHED -> extra.put("type", event.getNewValue());
            case EventNames.TICKET_ESCALATED -> extra.put("reason", event.getNewValue());
            default -> {
            }
        }
        return extra;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
