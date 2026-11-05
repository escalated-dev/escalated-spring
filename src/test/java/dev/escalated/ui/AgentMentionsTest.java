package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentNotificationRepository;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Mentions, as escalated-laravel's {@code MentionService} and {@code AgentSearchController}: agents
 * mentioned in an agent's reply or internal note get a {@code mention} notification, and the
 * composer's mention picker searches agents at {@code admin/agents/search}.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "escalated.ui.enabled=true",
    "escalated.ui.version=build-1",
    "escalated.ui.scripts[0]=/build/app.js",
})
class AgentMentionsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private AgentNotificationRepository notifications;
    @Autowired private TicketService tickets;

    private String unique;
    private AgentProfile author;
    private AgentProfile alice;
    private AgentProfile admin;
    private Ticket ticket;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        author = staff("Author " + unique, "author-" + unique + "@example.com", false);
        alice = staff("Alice " + unique, "alice-" + unique + "@example.com", false);
        admin = staff("Boss " + unique, "boss-" + unique + "@example.com", true);
        ticket = tickets.create("Mentions " + unique, "Help", "Customer", "customer-" + unique + "@example.com",
                TicketPriority.LOW, null);
    }

    @Test
    void anAgentMentionedInANoteIsNotified() throws Exception {
        tickets.addReply(ticket.getId(), "Can @{" + alice.getName() + "} look?", author.getName(), author.getEmail(),
                "agent", true);

        JsonNode item = feed(alice).get("data").get(0);
        assertThat(item.get("type").asText()).isEqualTo("mention");
        assertThat(item.get("title").asText())
                .isEqualTo(author.getName() + " mentioned you on " + ticket.getTicketNumber());
        assertThat(item.get("ticket").get("id").asLong()).isEqualTo(ticket.getId());
    }

    @Test
    void aMentionInAnAgentReplyMatchesByEmailIgnoringCase() throws Exception {
        tickets.addReply(ticket.getId(), "cc @{" + alice.getEmail().toUpperCase() + "}", author.getName(),
                author.getEmail(), "agent", false);

        assertThat(types(alice)).containsExactly("mention");
    }

    @Test
    void nobodyHearsOfTheirOwnMentionACustomersOrAnUnknownName() throws Exception {
        tickets.addReply(ticket.getId(), "@{" + author.getName() + "} @nobody" + unique, author.getName(),
                author.getEmail(), "agent", true);
        tickets.addReply(ticket.getId(), "Hi @{" + alice.getName() + "}", "Customer",
                "customer-" + unique + "@example.com", "customer", false);

        assertThat(types(author)).doesNotContain("mention");
        assertThat(types(alice)).doesNotContain("mention");
    }

    @Test
    void theMentionPickerListsAgentsByNameOrEmail() throws Exception {
        JsonNode found = search("alice-" + unique);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).get("id").asLong()).isEqualTo(alice.getId());
        assertThat(found.get(0).get("name").asText()).isEqualTo(alice.getName());
        assertThat(found.get(0).get("email").asText()).isEqualTo(alice.getEmail());

        assertThat(search(unique)).hasSize(3);
        assertThat(search("")).isEmpty();
        assertThat(search("customer-" + unique)).isEmpty();
    }

    @Test
    void theMentionPickerIsAnAdminRouteAsInLaravel() throws Exception {
        mvc.perform(get("/escalated/admin/agents/search").param("q", unique).with(user(alice.getEmail()))
                .header("Accept", "application/json")).andExpect(status().isForbidden());
    }

    private AgentProfile staff(String name, String email, boolean isAdmin) {
        AgentProfile profile = new AgentProfile();
        profile.setName(name);
        profile.setEmail(email);
        profile.setAgent(true);
        profile.setAdmin(isAdmin);
        return agents.save(profile);
    }

    private java.util.List<String> types(AgentProfile agent) {
        return notifications.findByAgentId(agent.getId(), PageRequest.of(0, 50)).stream()
                .map(dev.escalated.models.AgentNotification::getType).toList();
    }

    private JsonNode feed(AgentProfile agent) throws Exception {
        String body = mvc.perform(get("/escalated/agent/notifications").with(user(agent.getEmail()))
                        .header("Accept", "application/json"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private JsonNode search(String q) throws Exception {
        String body = mvc.perform(get("/escalated/admin/agents/search").param("q", q).with(user(admin.getEmail()))
                        .header("Accept", "application/json"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }
}
