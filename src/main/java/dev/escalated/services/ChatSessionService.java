package dev.escalated.services;

import dev.escalated.models.ChatSession;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.ChatSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        messagingTemplate.ifPresent(template -> template.convertAndSend(destination, payload));
    }

    /**
     * Start a new chat session. Creates an underlying ticket with channel "chat".
     */
    @Transactional
    public ChatSession start(String visitorName, String visitorEmail,
                             String initialMessage, Long departmentId) {
        Ticket ticket = ticketService.create(
                "Chat with " + visitorName,
                initialMessage != null ? initialMessage : "",
                visitorName,
                visitorEmail != null ? visitorEmail : "visitor@chat.local",
                TicketPriority.MEDIUM,
                departmentId);
        ticket.setChannel("chat");

        ChatSession session = new ChatSession();
        session.setTicketId(ticket.getId());
        session.setVisitorName(visitorName);
        session.setVisitorEmail(visitorEmail);
        session.setDepartmentId(departmentId);
        session.setStatus("waiting");
        session.setLastActivityAt(Instant.now());

        // Apply routing rules
        ChatRoutingService.RouteResult route = chatRoutingService.resolve(departmentId);
        if (route.departmentId() != null) {
            session.setDepartmentId(route.departmentId());
        }
        if (route.agentId() != null) {
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
    @Transactional
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
    @Transactional
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
    @Transactional
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
        return chatSessionRepository.findByAgentIdAndStatusOrderByLastActivityAtDesc(agentId, "active");
    }

    public int getQueueDepth(Long departmentId) {
        return chatSessionRepository.countWaiting(departmentId);
    }
}
