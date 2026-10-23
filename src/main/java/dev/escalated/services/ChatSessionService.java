package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.events.ChatEvent;
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

    /** Optional so the service can be built by hand without a context. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

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

    @org.springframework.beans.factory.annotation.Autowired
    private dev.escalated.repositories.AgentProfileRepository agentProfiles;

    @org.springframework.beans.factory.annotation.Autowired
    private dev.escalated.repositories.DepartmentRepository departments;

    @org.springframework.beans.factory.annotation.Autowired
    private dev.escalated.repositories.TicketRepository tickets;

    private void announce(ChatEvent.Type type, ChatSession session) {
        if (eventPublisher != null && session != null) {
            eventPublisher.publishEvent(new ChatEvent(this, type, session));
        }
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
        announce(ChatEvent.Type.STARTED, session);
        if (session.getAgentId() != null) {
            announce(ChatEvent.Type.ASSIGNED, session);
        }

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
        announce(ChatEvent.Type.ASSIGNED, session);

        return session;
    }

    /**
     * Hand a chat to another agent and/or department, as escalated-laravel's
     * ChatSessionService::transferChat. With an agent, the session and its
     * ticket are reassigned to that agent. With a department, the ticket
     * moves there; without an agent too, the chat goes back to the queue
     * (unassigned, waiting) and routing picks an agent for its new
     * department. Announces {@code chat.transferred} with the previous agent,
     * the new agent and the new department.
     *
     * @throws EntityNotFoundException for an unknown session
     * @throws IllegalArgumentException when neither target is given, the chat
     *         has ended, the department does not exist, or the agent is not
     *         active staff who can be assigned the ticket
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public ChatSession transfer(Long sessionId, Long toAgentId, Long toDepartmentId) {
        ChatSession session = findById(sessionId);

        if ("ended".equals(session.getStatus())) {
            throw new IllegalArgumentException("Chat session has already ended.");
        }
        if (toAgentId == null && toDepartmentId == null) {
            throw new IllegalArgumentException("Provide an agent_id or department_id.");
        }

        dev.escalated.models.Department department = null;
        if (toDepartmentId != null) {
            department = departments.findById(toDepartmentId)
                    .orElseThrow(() -> new IllegalArgumentException("The selected department_id is invalid."));
        }
        dev.escalated.models.AgentProfile agent = null;
        if (toAgentId != null) {
            agent = agentProfiles.findById(toAgentId)
                    .filter(candidate -> candidate.isActive() && (candidate.isAgent() || candidate.isAdmin()))
                    .orElseThrow(() -> new IllegalArgumentException("Agent cannot access this ticket."));
        }

        Ticket ticket = ticketService.findById(session.getTicketId());
        Long fromAgentId = session.getAgentId();
        final dev.escalated.models.Department targetDepartment = department;
        final dev.escalated.models.AgentProfile targetAgent = agent;
        java.util.function.Supplier<Ticket> move = () -> {
            if (targetDepartment != null) {
                ticket.setDepartment(targetDepartment);
            }
            // Checked against the ticket in its new department.
            if (targetAgent != null && access != null && !access.canAssign(ticket, targetAgent)) {
                throw new IllegalArgumentException("Agent cannot access this ticket.");
            }
            ticket.setAssignedAgent(targetAgent);
            return tickets.save(ticket);
        };
        // A refusal rolls the department change back with the transaction.
        if (access == null) {
            move.get();
        } else {
            access.withoutRestriction(move);
        }

        if (toDepartmentId != null) {
            session.setDepartmentId(toDepartmentId);
        }
        if (toAgentId != null) {
            session.setAgentId(toAgentId);
        } else {
            session.setAgentId(null);
            session.setStatus("waiting");
            session.setAcceptedAt(null);
        }
        session = chatSessionRepository.save(session);

        broadcast("/topic/chat/" + sessionId, session);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new ChatEvent(this, ChatEvent.Type.TRANSFERRED, session,
                    new ChatEvent.Transfer(fromAgentId, toAgentId, toDepartmentId)));
        }

        if (toAgentId == null) {
            ChatRoutingService.RouteResult route = chatRoutingService.resolve(toDepartmentId);
            boolean eligible = route.agentId() != null
                    && agentProfiles.findById(route.agentId())
                            .filter(candidate -> access == null || access.canAssign(ticket, candidate))
                            .isPresent();
            if (eligible) {
                session = accept(sessionId, route.agentId());
            }
        }

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
        announce(ChatEvent.Type.MESSAGE, session);

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
        announce(ChatEvent.Type.ENDED, session);

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
