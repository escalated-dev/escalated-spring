package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketActivity;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TicketActivityRepository;
import dev.escalated.services.ChatSessionService;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
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
 * Staff write routes record the signed-in user as the author or actor. They
 * used to take it from the request body, so an agent could post a reply as the
 * customer ({@code author_type: "customer"}) or as a colleague, and an admin
 * could write the activity log in anyone's name.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class StaffActorIdentityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TicketService ticketService;
    @Autowired private ReplyRepository replies;
    @Autowired private TicketActivityRepository activities;
    @Autowired private AgentProfileRepository agents;
    @Autowired private ChatSessionService chats;

    private String requester;
    private Ticket ticket;
    private AgentProfile agent;

    @BeforeEach
    void seed() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        requester = "customer-" + unique + "@example.com";
        ticket = ticketService.create("Help", "Body", "Customer", requester, TicketPriority.MEDIUM, null);
        agent = staff("Agent Ann " + unique, false);
    }

    @Test
    void anAgentsReplyIsFiledAsThatAgentWhateverTheBodySays() throws Exception {
        mockMvc.perform(post("/escalated/api/agent/tickets/{id}/replies", ticket.getId())
                        .with(user(agent.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"I take it all back\",\"author_name\":\"Customer\","
                                + "\"author_email\":\"" + requester + "\",\"author_type\":\"customer\"}"))
                .andExpect(status().isCreated());

        Reply reply = replies.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                .filter(r -> "I take it all back".equals(r.getBody()))
                .findFirst().orElseThrow();
        assertThat(reply.getAuthorEmail()).isEqualTo(agent.getEmail());
        assertThat(reply.getAuthorName()).isEqualTo(agent.getName());
        assertThat(reply.getAuthorType()).isEqualTo("agent");
    }

    @Test
    void anAgentsChatMessageIsSentAsThatAgent() throws Exception {
        Long sessionId = chats.startForWidget("Visitor", requester, "Hello?", null, null).session().getId();

        mockMvc.perform(post("/escalated/api/agent/chat/{id}/messages", sessionId)
                        .with(user(agent.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Spoofed\",\"name\":\"Someone Else\",\"email\":\"boss@example.com\"}"))
                .andExpect(status().isCreated());

        Long ticketId = chats.findById(sessionId).getTicketId();
        Reply reply = replies.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .filter(r -> "Spoofed".equals(r.getBody()))
                .findFirst().orElseThrow();
        assertThat(reply.getAuthorEmail()).isEqualTo(agent.getEmail());
        assertThat(reply.getAuthorName()).isEqualTo(agent.getName());
    }

    @Test
    void anAdminsStatusChangeIsLoggedAsThatAdmin() throws Exception {
        AgentProfile admin = staff("Admin Ada " + UUID.randomUUID(), true);

        mockMvc.perform(post("/escalated/api/admin/tickets/{id}/status", ticket.getId())
                        .with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"actor_email\":\"" + agent.getEmail() + "\"}"))
                .andExpect(status().isOk());

        assertThat(activities.findByTicketIdOrderByCreatedAtDesc(ticket.getId()))
                .extracting(TicketActivity::getActorEmail)
                .contains(admin.getEmail())
                .doesNotContain(agent.getEmail());
    }

    private AgentProfile staff(String name, boolean admin) {
        AgentProfile profile = new AgentProfile();
        profile.setName(name);
        profile.setEmail("staff-" + UUID.randomUUID() + "@example.com");
        profile.setAgent(true);
        profile.setAdmin(admin);
        return agents.save(profile);
    }
}
