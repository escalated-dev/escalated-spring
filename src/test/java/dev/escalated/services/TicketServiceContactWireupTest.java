package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.enums.TicketPriority;
import dev.escalated.models.Contact;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.ContactRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for the Contact dedupe wire-up in
 * {@link TicketService#create}. Matches the Pattern B test coverage in
 * the other framework PRs (NestJS, Laravel, Rails, Django, Adonis,
 * .NET, Symfony, Go, WordPress, Phoenix).
 */
@SpringBootTest
@Transactional
class TicketServiceContactWireupTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private ContactRepository contactRepository;

    @Test
    void create_withGuestEmail_createsContactAndLinksTicket() {
        Ticket ticket = ticketService.create(
                "Help", "body", "Alice", "alice@example.com",
                TicketPriority.MEDIUM, null);

        assertThat(ticket.getContact()).isNotNull();
        assertThat(ticket.getContact().getEmail()).isEqualTo("alice@example.com");
        assertThat(ticket.getContact().getName()).isEqualTo("Alice");
    }

    @Test
    void create_repeatEmail_dedupesOntoSameContact() {
        Ticket t1 = ticketService.create(
                "First", "body", "Alice", "alice@example.com",
                TicketPriority.MEDIUM, null);
        // Casing variant should dedupe.
        Ticket t2 = ticketService.create(
                "Second", "body", "Alice", "ALICE@Example.COM",
                TicketPriority.MEDIUM, null);

        assertThat(t1.getContact().getId()).isEqualTo(t2.getContact().getId());
        assertThat(contactRepository.findByEmail("alice@example.com")).isPresent();
    }

    @Test
    void create_blankExistingNameIsFilledIn() {
        Contact existing = new Contact();
        existing.setEmail("alice@example.com");
        existing.setName(null);
        contactRepository.save(existing);

        Ticket ticket = ticketService.create(
                "Help", "body", "Alice", "alice@example.com",
                TicketPriority.MEDIUM, null);

        assertThat(ticket.getContact().getName()).isEqualTo("Alice");
    }
}
