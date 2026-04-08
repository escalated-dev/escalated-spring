package dev.escalated.services;

import dev.escalated.models.ChatSession;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.ChatRoutingRuleRepository;
import dev.escalated.repositories.ChatSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private ChatRoutingRuleRepository routingRuleRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChatSessionService chatSessionService;
    private ChatRoutingService chatRoutingService;

    @BeforeEach
    void setUp() {
        chatRoutingService = new ChatRoutingService(routingRuleRepository);
        chatSessionService = new ChatSessionService(
                chatSessionRepository, ticketService, chatRoutingService, messagingTemplate);
    }

    private Ticket createMockTicket() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setSubject("Chat with Visitor");
        ticket.setBody("Hello");
        ticket.setRequesterName("Visitor");
        ticket.setRequesterEmail("visitor@test.com");
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setTicketNumber("TK-001");
        return ticket;
    }

    private ChatSession createMockSession(String status) {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setTicketId(1L);
        session.setVisitorName("Visitor");
        session.setStatus(status);
        session.setLastActivityAt(Instant.now());
        return session;
    }

    @Test
    void start_createsSessionAndTicket() {
        Ticket ticket = createMockTicket();
        when(ticketService.create(anyString(), anyString(), anyString(), anyString(),
                any(TicketPriority.class), any())).thenReturn(ticket);
        when(routingRuleRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession s = invocation.getArgument(0);
            s.setId(1L);
            return s;
        });

        ChatSession session = chatSessionService.start("Visitor", "v@test.com", "Hi", null);

        assertNotNull(session);
        assertEquals("Visitor", session.getVisitorName());
        assertEquals("waiting", session.getStatus());
        assertEquals(1L, session.getTicketId());
        verify(chatSessionRepository).save(any(ChatSession.class));
    }

    @Test
    void accept_transitionsToActive() {
        ChatSession session = createMockSession("waiting");
        Ticket ticket = createMockTicket();
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(ticketService.findById(1L)).thenReturn(ticket);
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(i -> i.getArgument(0));

        ChatSession accepted = chatSessionService.accept(1L, 42L);

        assertEquals("active", accepted.getStatus());
        assertEquals(42L, accepted.getAgentId());
        assertNotNull(accepted.getAcceptedAt());
    }

    @Test
    void accept_throwsForNonWaiting() {
        ChatSession session = createMockSession("active");
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> chatSessionService.accept(1L, 42L));
    }

    @Test
    void sendMessage_createsReply() {
        ChatSession session = createMockSession("active");
        Reply reply = new Reply();
        reply.setId(1L);
        reply.setBody("Hello");
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(ticketService.addReply(anyLong(), anyString(), anyString(), anyString(), anyString(),
                eq(false))).thenReturn(reply);
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(i -> i.getArgument(0));

        Reply result = chatSessionService.sendMessage(1L, "Hello", "Visitor", "v@test.com", "visitor");

        assertNotNull(result);
        assertEquals("Hello", result.getBody());
    }

    @Test
    void sendMessage_throwsForEndedSession() {
        ChatSession session = createMockSession("ended");
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class,
                () -> chatSessionService.sendMessage(1L, "msg", "V", "v@t.com", "visitor"));
    }

    @Test
    void end_transitionsToEnded() {
        ChatSession session = createMockSession("active");
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(i -> i.getArgument(0));

        ChatSession ended = chatSessionService.end(1L);

        assertEquals("ended", ended.getStatus());
        assertNotNull(ended.getEndedAt());
    }

    @Test
    void end_throwsForAlreadyEnded() {
        ChatSession session = createMockSession("ended");
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> chatSessionService.end(1L));
    }

    @Test
    void findById_throwsWhenNotFound() {
        when(chatSessionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> chatSessionService.findById(99L));
    }
}
