package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Reply;
import dev.escalated.models.RuleExecution;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AutomationRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.RuleExecutionRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.scheduling.AutomationScheduler;
import dev.escalated.services.AutomationRunner;
import dev.escalated.services.TicketService;
import hostapp.HostApplication;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Configurable, time-based automations (Laravel's AutomationRunner): admin
 * CRUD over HTTP with the shared form's body, fire-once per ticket with
 * {@code repeat_after_hours}, and the built-in auto-close kept as a setting.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class AdminAutomationsTest {

    private static final String BASE = "/escalated/api/admin/automations";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private AgentProfileRepository agents;
    @Autowired private AutomationRepository automations;
    @Autowired private RuleExecutionRepository executions;
    @Autowired private AutomationRunner runner;
    @Autowired private AutomationScheduler scheduler;
    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository tickets;
    @Autowired private ReplyRepository replies;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EscalatedProperties properties;

    private String unique;
    private AgentProfile admin;
    private final List<Long> created = new ArrayList<>();

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        admin = staff(true);
    }

    @AfterEach
    void cleanUp() {
        created.stream().filter(automations::existsById).forEach(automations::deleteById);
        properties.getAutomations().setAutoCloseDays(7);
    }

    /** What Escalated/Admin/Automations/Form posts. */
    private String formBody(String note, Object repeatAfterHours) {
        return """
                {
                  "name": "Nudge stale %s",
                  "conditions": [
                    { "field": "hours_since_created", "operator": ">", "value": 24 },
                    { "field": "subject_contains", "operator": ">", "value": "stale %s" }
                  ],
                  "actions": [{ "type": "add_note", "value": "%s" }],
                  "active": true,
                  "repeat_after_hours": %s
                }
                """.formatted(unique, unique, note, repeatAfterHours);
    }

    @Test
    void theFormsBodyIsStoredAndListed() throws Exception {
        JsonNode body = create(formBody("Still waiting", "null"));

        assertThat(body.get("name").asText()).isEqualTo("Nudge stale " + unique);
        assertThat(body.get("conditions").get(0).get("field").asText()).isEqualTo("hours_since_created");
        assertThat(body.get("conditions").get(0).get("value").asInt()).isEqualTo(24);
        assertThat(body.get("actions").get(0).get("type").asText()).isEqualTo("add_note");
        assertThat(body.get("active").asBoolean()).isTrue();
        assertThat(body.get("repeat_after_hours").isNull()).isTrue();

        mockMvc.perform(get(BASE).with(user(admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(body.get("id").asInt())));
        mockMvc.perform(get(BASE + "/options").with(user(admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condition_fields", hasItem("hours_since_updated")))
                .andExpect(jsonPath("$.action_types", hasItem("set_ticket_type")));
    }

    @Test
    void anAutomationActsOnceOnAMatchingTicket() throws Exception {
        create(formBody("Still waiting " + unique, "null"));
        Ticket stale = ticket("Something stale " + unique, Duration.ofHours(30));
        Ticket fresh = ticket("Something stale " + unique + " but new", Duration.ofHours(2));

        runner.run();
        runner.run();

        assertThat(notes(stale, "Still waiting " + unique)).hasSize(1);
        assertThat(notes(fresh, "Still waiting " + unique)).isEmpty();
    }

    @Test
    void repeatAfterHoursLetsItActAgain() throws Exception {
        long id = create(formBody("Ping " + unique, 1)).get("id").asLong();
        Ticket stale = ticket("Very stale " + unique, Duration.ofHours(48));

        runner.run();
        runner.run();
        assertThat(notes(stale, "Ping " + unique)).hasSize(1);

        // Two hours later the hour has passed and the ticket still matches.
        jdbc.update("UPDATE escalated_rule_executions SET fired_at = ? WHERE rule_type = ? AND rule_id = ? AND ticket_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofHours(2))), RuleExecution.AUTOMATION, id, stale.getId());
        runner.run();

        assertThat(notes(stale, "Ping " + unique)).hasSize(2);
    }

    @Test
    void invalidBodiesAnswer422() throws Exception {
        mockMvc.perform(post(BASE).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"conditions\":[],\"actions\":[{\"type\":\"explode\"}],"
                                + "\"repeat_after_hours\":0}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasKey("name")))
                .andExpect(jsonPath("$.errors", hasKey("conditions")))
                .andExpect(jsonPath("$.errors", hasKey("actions.0.type")))
                .andExpect(jsonPath("$.errors", hasKey("repeat_after_hours")));
    }

    @Test
    void updateKeepsRepeatAfterHoursWhenNotSentAndDeleteForgetsTheFireLog() throws Exception {
        long id = create(formBody("Kept " + unique, 12)).get("id").asLong();

        mockMvc.perform(put(BASE + "/{id}", id).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed " + unique + "\","
                                + "\"conditions\":[{\"field\":\"status\",\"value\":\"open\"}],"
                                + "\"actions\":[{\"type\":\"change_priority\",\"value\":\"high\"}],\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed " + unique))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.repeat_after_hours").value(12));

        mockMvc.perform(post(BASE + "/{id}/toggle", id).with(user(admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        Ticket ticket = ticket("Delete me " + unique, Duration.ofHours(1));
        RuleExecution execution = new RuleExecution();
        execution.setRuleType(RuleExecution.AUTOMATION);
        execution.setRuleId(id);
        execution.setTicketId(ticket.getId());
        execution.setFiredAt(Instant.now());
        executions.save(execution);

        mockMvc.perform(delete(BASE + "/{id}", id).with(user(admin.getEmail())))
                .andExpect(status().isNoContent());
        assertThat(automations.existsById(id)).isFalse();
        assertThat(executions.findByRuleTypeAndRuleId(RuleExecution.AUTOMATION, id)).isEmpty();
    }

    @Test
    void agentsWhoAreNotAdminsCannotManageAutomations() throws Exception {
        mockMvc.perform(post(BASE).with(user(staff(false).getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formBody("x", "null")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theBuiltInAutoCloseStillClosesResolvedTicketsAfterSevenDays() {
        Ticket old = resolved(Duration.ofDays(8));
        Ticket recent = resolved(Duration.ofDays(2));

        scheduler.autoCloseResolvedTickets();

        assertThat(tickets.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(tickets.findById(recent.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void theBuiltInAutoCloseCanBeTurnedOff() {
        properties.getAutomations().setAutoCloseDays(0);
        Ticket old = resolved(Duration.ofDays(30));

        scheduler.autoCloseResolvedTickets();

        assertThat(tickets.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    private JsonNode create(String body) throws Exception {
        String response = mockMvc.perform(post(BASE).with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = JSON.readTree(response);
        created.add(node.get("id").asLong());
        return node;
    }

    private Ticket ticket(String subject, Duration age) {
        Ticket ticket = ticketService.create(subject, "Body", "Customer",
                "customer-" + unique + "@example.com", TicketPriority.LOW, null);
        Timestamp at = Timestamp.from(Instant.now().minus(age));
        jdbc.update("UPDATE escalated_tickets SET created_at = ?, updated_at = ? WHERE id = ?", at, at, ticket.getId());
        return ticket;
    }

    private Ticket resolved(Duration ago) {
        Ticket ticket = ticketService.create("Resolved " + unique, "Body", "Customer",
                "customer-" + unique + "@example.com", TicketPriority.LOW, null);
        jdbc.update("UPDATE escalated_tickets SET status = 'RESOLVED', resolved_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(ago)), ticket.getId());
        return ticket;
    }

    private List<Reply> notes(Ticket ticket, String body) {
        return replies.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                .filter(reply -> body.equals(reply.getBody()) && reply.isInternal())
                .toList();
    }

    private AgentProfile staff(boolean isAdmin) {
        AgentProfile profile = new AgentProfile();
        profile.setName("Staff " + unique);
        profile.setEmail("staff-" + UUID.randomUUID() + "@example.com");
        profile.setAgent(true);
        profile.setAdmin(isAdmin);
        return agents.save(profile);
    }
}
