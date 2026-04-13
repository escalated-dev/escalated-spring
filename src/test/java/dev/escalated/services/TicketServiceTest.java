package dev.escalated.services;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ChatSessionRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketActivityRepository;
import dev.escalated.repositories.TicketLinkRepository;
import dev.escalated.repositories.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private ReplyRepository replyRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private TicketActivityRepository activityRepository;
    @Mock
    private AgentProfileRepository agentRepository;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private TicketLinkRepository ticketLinkRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SlaService slaService;
    @Mock
    private AuditLogService auditLogService;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, replyRepository, tagRepository,
                activityRepository, agentRepository, chatSessionRepository, ticketLinkRepository,
                eventPublisher, slaService, auditLogService);
    }

    @Test
    void createTicket_shouldSetDefaultsAndSave() {
        when(ticketRepository.count()).thenReturn(0L);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(activityRepository.save(any())).thenReturn(null);

        Ticket ticket = ticketService.create("Test Subject", "Test body",
                "John Doe", "john@example.com", TicketPriority.HIGH, null);

        assertNotNull(ticket);
        assertEquals("Test Subject", ticket.getSubject());
        assertEquals("Test body", ticket.getBody());
        assertEquals(TicketPriority.HIGH, ticket.getPriority());
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        assertNotNull(ticket.getTicketNumber());
        assertNotNull(ticket.getGuestAccessToken());
        verify(slaService).applySlaPolicy(any());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void createTicket_shouldUseDefaultPriority() {
        when(ticketRepository.count()).thenReturn(0L);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(activityRepository.save(any())).thenReturn(null);

        Ticket ticket = ticketService.create("Test", "Body", "User", "user@test.com", null, null);

        assertEquals(TicketPriority.MEDIUM, ticket.getPriority());
    }

    @Test
    void findById_shouldThrowWhenNotFound() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> ticketService.findById(999L));
    }

    @Test
    void changeStatus_shouldUpdateAndPublishEvent() {
        Ticket ticket = createTestTicket();
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);
        when(activityRepository.save(any())).thenReturn(null);

        Ticket result = ticketService.changeStatus(1L, TicketStatus.RESOLVED, "agent@test.com");

        assertEquals(TicketStatus.RESOLVED, result.getStatus());
        assertNotNull(result.getResolvedAt());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void changeStatus_shouldSetClosedAt() {
        Ticket ticket = createTestTicket();
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);
        when(activityRepository.save(any())).thenReturn(null);

        Ticket result = ticketService.changeStatus(1L, TicketStatus.CLOSED, "agent@test.com");

        assertEquals(TicketStatus.CLOSED, result.getStatus());
        assertNotNull(result.getClosedAt());
    }

    @Test
    void changeStatus_shouldNoopWhenSameStatus() {
        Ticket ticket = createTestTicket();
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        Ticket result = ticketService.changeStatus(1L, TicketStatus.OPEN, "agent@test.com");

        assertEquals(TicketStatus.OPEN, result.getStatus());
    }

    @Test
    void assign_shouldSetAgent() {
        Ticket ticket = createTestTicket();
        AgentProfile agent = new AgentProfile();
        agent.setId(5L);
        agent.setName("Jane Agent");

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(agentRepository.findById(5L)).thenReturn(Optional.of(agent));
        when(ticketRepository.save(any())).thenReturn(ticket);
        when(activityRepository.save(any())).thenReturn(null);

        Ticket result = ticketService.assign(1L, 5L, "admin@test.com");

        assertEquals(agent, result.getAssignedAgent());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void addReply_shouldCreateReplyAndRecordFirstResponse() {
        Ticket ticket = createTestTicket();
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(replyRepository.save(any(Reply.class))).thenAnswer(inv -> {
            Reply r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(activityRepository.save(any())).thenReturn(null);
        when(ticketRepository.save(any())).thenReturn(ticket);

        Reply reply = ticketService.addReply(1L, "Thanks for reaching out",
                "Agent", "agent@test.com", "agent", false);

        assertNotNull(reply);
        assertEquals("Thanks for reaching out", reply.getBody());
        assertNotNull(ticket.getFirstRespondedAt());
    }

    @Test
    void addReply_shouldNotRecordFirstResponseForInternalNote() {
        Ticket ticket = createTestTicket();
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(replyRepository.save(any(Reply.class))).thenAnswer(inv -> {
            Reply r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(activityRepository.save(any())).thenReturn(null);

        ticketService.addReply(1L, "Internal note", "Agent", "agent@test.com", "agent", true);

        assertEquals(null, ticket.getFirstRespondedAt());
    }

    @Test
    void snooze_shouldSetStatusAndUntil() {
        Ticket ticket = createTestTicket();
        Instant until = Instant.now().plusSeconds(3600);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);
        when(activityRepository.save(any())).thenReturn(null);

        Ticket result = ticketService.snooze(1L, until, "agent@test.com");

        assertEquals(TicketStatus.SNOOZED, result.getStatus());
        assertEquals(until, result.getSnoozedUntil());
    }

    @Test
    void wakeUpSnoozedTickets_shouldUnsnoozeDueTickets() {
        Ticket snoozed = createTestTicket();
        snoozed.setStatus(TicketStatus.SNOOZED);
        snoozed.setSnoozedUntil(Instant.now().minusSeconds(60));

        when(ticketRepository.findSnoozedTicketsDue(any())).thenReturn(List.of(snoozed));
        when(ticketRepository.save(any())).thenReturn(snoozed);
        when(activityRepository.save(any())).thenReturn(null);

        ticketService.wakeUpSnoozedTickets();

        assertEquals(TicketStatus.OPEN, snoozed.getStatus());
        assertEquals(null, snoozed.getSnoozedUntil());
    }

    @Test
    void merge_shouldMoveRepliesAndMarkSource() {
        Ticket source = createTestTicket();
        source.setTicketNumber("ESC-000001");

        Ticket target = new Ticket();
        target.setId(2L);
        target.setTicketNumber("ESC-000002");
        target.setSubject("Target");

        Reply reply = new Reply();
        reply.setId(1L);
        reply.setBody("Original reply");
        reply.setAuthorName("User");
        reply.setAuthorEmail("user@test.com");
        reply.setAuthorType("customer");

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(source));
        when(ticketRepository.findById(2L)).thenReturn(Optional.of(target));
        when(replyRepository.findByTicketIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(reply));
        when(replyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(activityRepository.save(any())).thenReturn(null);

        Ticket result = ticketService.merge(1L, 2L, "admin@test.com");

        assertEquals(TicketStatus.MERGED, source.getStatus());
        assertEquals(2L, source.getMergedIntoTicketId());
        assertEquals(target, result);
    }

    @Test
    void split_shouldCreateNewTicket() {
        Ticket original = createTestTicket();
        original.setTicketNumber("ESC-000001");
        original.setRequesterName("User");
        original.setRequesterEmail("user@test.com");

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(original));
        when(ticketRepository.count()).thenReturn(1L);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(2L);
            }
            return t;
        });
        when(activityRepository.save(any())).thenReturn(null);

        Ticket result = ticketService.split(1L, "Split Subject", null, "admin@test.com");

        assertNotNull(result);
        assertEquals("Split Subject", result.getSubject());
        assertEquals("user@test.com", result.getRequesterEmail());
    }

    private Ticket createTestTicket() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setSubject("Test Ticket");
        ticket.setBody("Test body");
        ticket.setTicketNumber("ESC-000001");
        ticket.setRequesterName("John");
        ticket.setRequesterEmail("john@test.com");
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(TicketPriority.MEDIUM);
        return ticket;
    }
}
