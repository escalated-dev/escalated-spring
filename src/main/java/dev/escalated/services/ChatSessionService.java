package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.ChatSession;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.ChatSessionRepository;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Manages live-chat session lifecycle: creation, acceptance by an agent,
 * sending messages (stored as ticket replies), and ending sessions.
 *
 * <p>The {@link SimpMessagingTemplate} dependency is optional because
 * STOMP/WebSocket support is gated behind {@code escalated.broadcasting.enabled}.
 * When broadcasting is disabled, persistence still works correctly; only the
 * out-of-band fan-out to subscribed STOMP topics is skipped.
 */
@Service
public class ChatSessionService {
    @org.springframework.beans.factory.annotation.Autowired
    private dev.escalated.security.TicketAccess access;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<
                    dev.escalated.security.AuthorizedTicketBroadcaster>
            broadcasters;

    private final ChatSessionRepository chatSessionRepository;
    private final TicketService ticketService;
    private final ChatRoutingService chatRoutingService;
    private final Optional<SimpMessagingTemplate> messagingTemplate;

    public ChatSessionService(ChatSessionRepository chatSessionRepository,
                              TicketService ticketService,
                              ChatRoutingService chatRoutingService,
                              Optional<SimpMessagingTemplate> messagingTemplate) {
        this.chatSessionRepository = chatSessionRepository;
        this.ticketService = ticketService;
        this.chatRoutingService = chatRoutingService;
        this.messagingTemplate = messagingTemplate;
    }

    private void broadcast(String destination, Object payload) {
        if (access != null && access.enabled()) {
            var broadcaster = broadcasters.getIfAvailable();
            Long ticketId =
                    payload instanceof ChatSession session
                            ? session.getTicketId()
                            : payload instanceof Reply reply ? reply.getTicket().getId() : null;
            if (broadcaster != null && ticketId != null) {
                broadcaster.deliver(ticketId, destination, payload);
            }
            return;
        }
        messagingTemplate.ifPresent(template -> template.convertAndSend(destination, payload));
    }

    /**
     * Start a new chat session. Creates an underlying ticket with channel "chat".
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public ChatSession start(String visitorName, String visitorEmail,
                             String initialMessage, Long departmentId) {
        return start(visitorName, visitorEmail, initialMessage, departmentId, null);
    }

    /**
     * Start a chat whose ticket carries host metadata (see {@code WidgetMetadata}).
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public ChatSession start(String visitorName, String visitorEmail,
                             String initialMessage, Long departmentId,
                             java.util.Map<String, Object> metadata) {
        return start(visitorName, visitorEmail, initialMessage, departmentId, metadata, null);
    }

    /**
     * Start a chat from the public widget. The widget addresses the chat by
     * the returned random token from then on; only its hash is stored, and
     * the numeric session id is never accepted from the widget.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public WidgetChat startForWidget(String visitorName, String visitorEmail,
                                     String initialMessage, Long departmentId,
                                     java.util.Map<String, Object> metadata) {
        String token = WidgetChatTokens.generate();
        ChatSession session = start(visitorName, visitorEmail, initialMessage, departmentId,
                metadata, WidgetChatTokens.hash(token));
        return new WidgetChat(session, token);
    }

    /** A chat started from the widget, with the token the widget uses for it. */
    public record WidgetChat(ChatSession session, String token) {
    }

    /**
     * The chat a widget token belongs to.
     *
     * @throws EntityNotFoundException for a blank or unknown token
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public ChatSession findByWidgetToken(String token) {
        if (token == null || token.isBlank()) {
            throw new EntityNotFoundException("Chat session not found");
        }
        return chatSessionRepository.findByWidgetTokenHash(WidgetChatTokens.hash(token))
                .orElseThrow(() -> new EntityNotFoundException("Chat session not found"));
    }

    /** Tell the agents' side that the visitor is typing. Nothing is stored. */
    public void visitorTyping(ChatSession session) {
        broadcast("/topic/chat/" + session.getId() + "/typing",
                java.util.Map.of("name", session.getVisitorName(), "is_agent", false));
    }

    private ChatSession start(String visitorName, String visitorEmail,
                              String initialMessage, Long departmentId,
                              java.util.Map<String, Object> metadata, String widgetTokenHash) {
        Ticket ticket = ticketService.create(
                "Chat with " + visitorName,
                initialMessage != null ? initialMessage : "",
                visitorName,
                visitorEmail != null ? visitorEmail : "visitor@chat.local",
                TicketPriority.MEDIUM,
                departmentId);
        ticket.setChannel("chat");
        if (metadata != null) {
            ticket.setMetadata(metadata);
        }

        ChatSession session = new ChatSession();
        session.setTicketId(ticket.getId());
        session.setVisitorName(visitorName);
        session.setVisitorEmail(visitorEmail);
        session.setDepartmentId(departmentId);
        session.setStatus("waiting");
        session.setLastActivityAt(Instant.now());
        session.setWidgetTokenHash(widgetTokenHash);

        // Apply routing rules
        ChatRoutingService.RouteResult route = chatRoutingService.resolve(departmentId);
        if (route.departmentId() != null) {
            session.setDepartmentId(route.departmentId());
            if (!java.util.Objects.equals(route.departmentId(), departmentId)) {
                ticket =
                        ticketService.changeDepartment(
                                ticket.getId(), route.departmentId(), "system");
            }
        }
        if (route.agentId() != null) {
            ticketService.assign(ticket.getId(), route.agentId(), "system");
            session.setAgentId(route.agentId());
            session.setStatus("active");
            session.setAcceptedAt(Instant.now());
        }

        session = chatSessionRepository.save(session);

        broadcast("/topic/chat-queue", session);

        return session;
    }

    /**
     * Agent accepts a waiting chat session.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public ChatSession accept(Long sessionId, Long agentId) {
        ChatSession session = findById(sessionId);

        if (!"waiting".equals(session.getStatus())) {
            throw new IllegalStateException("Chat session is not in a waiting state.");
        }

        session.setAgentId(agentId);
        session.setStatus("active");
        session.setAcceptedAt(Instant.now());

        Ticket ticket = ticketService.findById(session.getTicketId());
        ticketService.assign(ticket.getId(), agentId, "system");

        session = chatSessionRepository.save(session);

        broadcast("/topic/chat/" + sessionId, session);

        return session;
    }

    /**
     * Send a message within a chat session.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Reply sendMessage(Long sessionId, String body, String authorName,
                             String authorEmail, String authorType) {
        ChatSession session = findById(sessionId);

        if ("ended".equals(session.getStatus())) {
            throw new IllegalStateException("Chat session has ended.");
        }

        Reply reply = ticketService.addReply(
                session.getTicketId(), body, authorName, authorEmail, authorType, false);

        session.setLastActivityAt(Instant.now());
        chatSessionRepository.save(session);

        broadcast("/topic/chat/" + sessionId + "/messages", reply);

        return reply;
    }

    /**
     * End a chat session. The underlying ticket is resolved.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public ChatSession end(Long sessionId) {
        ChatSession session = findById(sessionId);

        if ("ended".equals(session.getStatus())) {
            throw new IllegalStateException("Chat session has already ended.");
        }

        session.setStatus("ended");
        session.setEndedAt(Instant.now());

        ticketService.changeStatus(session.getTicketId(), TicketStatus.RESOLVED, "system");

        session = chatSessionRepository.save(session);

        broadcast("/topic/chat/" + sessionId, session);

        return session;
    }

    public ChatSession findById(Long id) {
        return chatSessionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Chat session not found: " + id));
    }

    public List<ChatSession> getWaitingSessions() {
        return chatSessionRepository.findByStatusOrderByCreatedAtAsc("waiting");
    }

    public List<ChatSession> getActiveSessionsForAgent(Long agentId) {
        return chatSessionRepository.findByAgentIdAndStatusOrderByLastActivityAtDesc(
                agentId, "active");
    }

    public int getQueueDepth(Long departmentId) {
        return chatSessionRepository.countWaiting(departmentId);
    }
}
