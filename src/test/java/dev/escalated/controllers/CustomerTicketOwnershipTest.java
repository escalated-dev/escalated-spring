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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.ChatSessionService;
import dev.escalated.services.TicketLinkService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.util.ArrayList;
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
    @Autowired private TicketLinkService links;
    @Autowired private ChatSessionService chats;

    private final ObjectMapper json = new ObjectMapper();

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

    @Test
    void theOwnersTicketHoldsNoInternalNotesLinkedTicketsOrStaffData() throws Exception {
        String note = "INTERNAL-NOTE-" + UUID.randomUUID();
        String agentEmail = "agent-" + UUID.randomUUID() + "@example.com";
        ticketService.addReply(alicesTicket.getId(), "Public answer", "Agent Ann", agentEmail, "agent", false);
        ticketService.addReply(alicesTicket.getId(), note, "Agent Ann", agentEmail, "agent", true);
        links.create(alicesTicket.getId(), bobsTicket.getId(), "related");

        String raw = mockMvc.perform(get(CUSTOMER + "/{id}", alicesTicket.getId()).with(user(alice)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(raw)
                .doesNotContain(note)
                .doesNotContain("Bob's medical claim")
                .doesNotContain(bobsTicket.getTicketNumber())
                .doesNotContain(agentEmail)
                .doesNotContain("related_tickets")
                .doesNotContain("requester_ticket_count")
                .doesNotContain("activities");
        if (alicesTicket.getGuestAccessToken() != null) {
            assertThat(raw).doesNotContain(alicesTicket.getGuestAccessToken());
        }
    }

    @Test
    void theOwnersTicketIsTheCustomerView() throws Exception {
        ticketService.addReply(alicesTicket.getId(), "Public answer", "Agent Ann", "agent@example.com", "agent", false);

        String raw = mockMvc.perform(get(CUSTOMER + "/{id}", alicesTicket.getId()).with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(alicesTicket.getTicketNumber()))
                .andExpect(jsonPath("$.subject").value("Alice's order"))
                .andExpect(jsonPath("$.description").value("Mine"))
                .andExpect(jsonPath("$.replies.length()").value(1))
                .andExpect(jsonPath("$.replies[0].body").value("Public answer"))
                .andExpect(jsonPath("$.replies[0].is_agent").value(true))
                .andExpect(jsonPath("$.satisfaction_rating").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(fieldNames(json.readTree(raw))).containsExactlyInAnyOrder(
                "id", "reference", "subject", "description", "status", "priority", "channel",
                "department", "requester_name", "created_at", "updated_at", "resolved_at",
                "closed_at", "attachments", "replies", "satisfaction_rating", "chat_messages");
    }

    @Test
    void aChatTicketsMessagesHoldNoInternalNotes() throws Exception {
        String note = "CHAT-NOTE-" + UUID.randomUUID();
        Long ticketId = chats.startForWidget("Alice", alice, "Hello?", null, null).session().getTicketId();
        ticketService.addReply(ticketId, "Hi Alice", "Agent Ann", "agent@example.com", "agent", false);
        ticketService.addReply(ticketId, note, "Agent Ann", "agent@example.com", "agent", true);

        String raw = mockMvc.perform(get(CUSTOMER + "/{id}", ticketId).with(user(alice)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(raw).doesNotContain(note).doesNotContain("agent@example.com");
        JsonNode body = json.readTree(raw);
        assertThat(body.get("channel").asText()).isEqualTo("chat");
        assertThat(body.get("chat_messages").findValuesAsText("body")).contains("Hi Alice");
        assertThat(body.has("chat_metadata")).isFalse();
        assertThat(body.has("chat_session_id")).isFalse();
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

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
