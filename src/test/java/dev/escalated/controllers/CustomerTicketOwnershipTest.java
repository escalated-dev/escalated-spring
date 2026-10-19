package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The customer ticket endpoints serve the signed-in user's own tickets only.
 * The customer is identified the way the rest of the library identifies a
 * principal: its name is the email address, matched against the ticket's
 * requester email. Anyone else's ticket answers 404, as if it did not exist.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class CustomerTicketOwnershipTest {

    private static final String CUSTOMER = "/escalated/api/customer/tickets";

    @Autowired private MockMvc mockMvc;
    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository tickets;
    @Autowired private ReplyRepository replies;

    private String alice;
    private String bob;
    private Ticket alicesTicket;
    private Ticket bobsTicket;

    @BeforeEach
    void seed() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        alice = "alice-" + run + "@example.com";
        bob = "bob-" + run + "@example.com";
        alicesTicket = ticketService.create("Alice's order", "Mine", "Alice", alice, TicketPriority.MEDIUM, null);
        bobsTicket = ticketService.create("Bob's medical claim", "Private", "Bob", bob, TicketPriority.MEDIUM, null);
        ticketService.addReply(bobsTicket.getId(), "Bob's private details", "Bob", bob, "customer", false);
    }

    // --- index ---

    @Test
    void theListIsTheSignedInUsersOwnTickets() throws Exception {
        mockMvc.perform(get(CUSTOMER).with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("Alice's order"));
    }

    @Test
    void anotherUsersEmailInTheQueryDoesNotListTheirTickets() throws Exception {
        mockMvc.perform(get(CUSTOMER).param("email", bob).with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("Alice's order"));
    }

    @Test
    void theRequesterEmailMatchesWhateverItsCase() throws Exception {
        mockMvc.perform(get(CUSTOMER).with(user(alice.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(CUSTOMER + "/{id}", alicesTicket.getId()).with(user(alice.toUpperCase())))
                .andExpect(status().isOk());
    }

    // --- show / replies ---

    @Test
    void theOwnerSeesTheirTicket() throws Exception {
        mockMvc.perform(get(CUSTOMER + "/{id}", alicesTicket.getId()).with(user(alice)))
                .andExpect(status().isOk());
        mockMvc.perform(get(CUSTOMER + "/{id}/replies", alicesTicket.getId()).with(user(alice)))
                .andExpect(status().isOk());
    }

    @Test
    void anotherUsersTicketIsNotFound() throws Exception {
        mockMvc.perform(get(CUSTOMER + "/{id}", bobsTicket.getId()).with(user(alice)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(CUSTOMER + "/{id}/replies", bobsTicket.getId()).with(user(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersTicketCannotBeRepliedTo() throws Exception {
        mockMvc.perform(post(CUSTOMER + "/{id}/replies", bobsTicket.getId())
                        .with(user(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Injected\",\"name\":\"Bob\",\"email\":\"" + bob + "\"}"))
                .andExpect(status().isNotFound());

        List<Reply> onBobs = replies.findByTicketIdOrderByCreatedAtAsc(bobsTicket.getId());
        assertThat(onBobs).extracting(Reply::getBody).doesNotContain("Injected");
    }

    @Test
    void theOwnersReplyIsFiledAsThemWhateverTheBodySays() throws Exception {
        mockMvc.perform(post(CUSTOMER + "/{id}/replies", alicesTicket.getId())
                        .with(user(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Any news?\",\"name\":\"The CEO\",\"email\":\"ceo@victim.example\"}"))
                .andExpect(status().isCreated());

        Reply reply = replies.findByTicketIdOrderByCreatedAtAsc(alicesTicket.getId()).stream()
                .filter(r -> "Any news?".equals(r.getBody()))
                .findFirst().orElseThrow();
        assertThat(reply.getAuthorEmail()).isEqualTo(alice);
        assertThat(reply.getAuthorName()).isEqualTo("Alice");
    }

    @Test
    void anInternalNoteIsNotInTheCustomersReplies() throws Exception {
        ticketService.addReply(alicesTicket.getId(), "Agents only", "Agent", "agent@example.com", "agent", true);

        mockMvc.perform(get(CUSTOMER + "/{id}/replies", alicesTicket.getId()).with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.body == 'Agents only')]").isEmpty());
    }

    // --- store ---

    @Test
    void aNewTicketIsFiledUnderTheSignedInUser() throws Exception {
        mockMvc.perform(post(CUSTOMER)
                        .with(user(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Filed as\",\"body\":\"Who?\",\"name\":\"Bob\",\"email\":\"" + bob + "\"}"))
                .andExpect(status().isCreated());

        assertThat(tickets.findAll()).filteredOn(t -> "Filed as".equals(t.getSubject()))
                .singleElement()
                .satisfies(t -> assertThat(t.getRequesterEmail()).isEqualTo(alice));
    }

    @Test
    void anonymousRequestsAreRefused() throws Exception {
        mockMvc.perform(get(CUSTOMER).param("email", bob)).andExpect(status().is4xxClientError());
    }
}
