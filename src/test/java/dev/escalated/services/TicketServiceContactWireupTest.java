package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.escalated.models.Contact;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ChatSessionRepository;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketActivityRepository;
import dev.escalated.repositories.TicketLinkRepository;
import dev.escalated.repositories.TicketRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for the Contact dedupe wire-up in
 * {@link TicketService#create}. Matches the Pattern B coverage in the
 * other framework PRs (NestJS, Laravel, Rails, Django, Adonis, .NET,
 * Symfony, Go, WordPress, Phoenix). Uses Mockito rather than
 * {@code @SpringBootTest} to match the rest of the test suite.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceContactWireupTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private ReplyRepository replyRepository;
    @Mock private TagRepository tagRepository;
    @Mock private TicketActivityRepository activityRepository;
    @Mock private AgentProfileRepository agentRepository;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private TicketLinkRepository ticketLinkRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SlaService slaService;
    @Mock private AuditLogService auditLogService;
    @Mock private ContactRepository contactRepository;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, replyRepository, tagRepository,
                activityRepository, agentRepository, chatSessionRepository, ticketLinkRepository,
                eventPublisher, slaService, auditLogService, contactRepository);

        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
    }

    @Test
    void create_withGuestEmail_looksUpContactByNormalizedEmail() {
        when(contactRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.empty());
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> {
            Contact c = inv.getArgument(0);
            c.setId(42L);
            return c;
        });

        Ticket ticket = ticketService.create(
                "Help", "body", "Alice", "alice@example.com",
                TicketPriority.MEDIUM, null);

        // Normalized lookup happens once.
        verify(contactRepository).findByEmail("alice@example.com");
        // No match → created with normalized email + name.
        verify(contactRepository).save(any(Contact.class));
        assertThat(ticket.getContact()).isNotNull();
        assertThat(ticket.getContact().getEmail()).isEqualTo("alice@example.com");
        assertThat(ticket.getContact().getName()).isEqualTo("Alice");
    }

    @Test
    void create_casingVariantEmailNormalizesBeforeLookup() {
        when(contactRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.empty());
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.create(
                "Second", "body", "Alice", "ALICE@Example.COM",
                TicketPriority.MEDIUM, null);

        verify(contactRepository).findByEmail("alice@example.com");
    }

    @Test
    void create_existingContactWithBlankNameIsUpdated() {
        Contact existing = new Contact();
        existing.setId(7L);
        existing.setEmail("alice@example.com");
        existing.setName(null);

        when(contactRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(existing));
        when(contactRepository.save(existing)).thenReturn(existing);

        Ticket ticket = ticketService.create(
                "Help", "body", "Alice", "alice@example.com",
                TicketPriority.MEDIUM, null);

        assertThat(ticket.getContact()).isSameAs(existing);
        assertThat(existing.getName()).isEqualTo("Alice");
        verify(contactRepository).save(eq(existing));
    }

    @Test
    void create_existingContactWithNamePreservesExistingNameAndDoesNotWriteBack() {
        Contact existing = new Contact();
        existing.setId(7L);
        existing.setEmail("alice@example.com");
        existing.setName("Original Alice");

        when(contactRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(existing));

        Ticket ticket = ticketService.create(
                "Second", "body", "New Alice", "alice@example.com",
                TicketPriority.MEDIUM, null);

        assertThat(ticket.getContact()).isSameAs(existing);
        assertThat(existing.getName()).isEqualTo("Original Alice");
        verify(contactRepository, never()).save(any(Contact.class));
    }

    @Test
    void create_blankEmailSkipsContactResolution() {
        Ticket ticket = ticketService.create(
                "Anon", "body", null, "   ",
                TicketPriority.MEDIUM, null);

        assertThat(ticket.getContact()).isNull();
        verify(contactRepository, never()).findByEmail(any());
        verify(contactRepository, never()).save(any(Contact.class));
    }
}
