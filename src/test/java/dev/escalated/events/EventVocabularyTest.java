package dev.escalated.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.models.EscalationRule;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketActivity;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.Workflow;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.EscalationRuleRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketActivityRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.repositories.WorkflowLogRepository;
import dev.escalated.repositories.WorkflowRepository;
import dev.escalated.scheduling.SlaScheduler;
import dev.escalated.services.ChatSessionService;
import dev.escalated.services.TicketService;
import dev.escalated.services.WebhookService;
import dev.escalated.services.WorkflowEngine;
import hostapp.HostApplication;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The shared event vocabulary (ADR 2026-10-21) end to end: the routes and
 * sweeps that change a ticket announce the canonical names to webhooks and run
 * the workflows saved under them, including workflows saved under a legacy
 * name.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class EventVocabularyTest {

    private static final Duration PATIENCE = Duration.ofSeconds(10);
    private static final long WAIT_MS = 10_000;

    @Autowired private MockMvc mockMvc;
    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository tickets;
    @Autowired private TagRepository tags;
    @Autowired private WorkflowRepository workflows;
    @Autowired private WorkflowLogRepository workflowLogs;
    @Autowired private AgentProfileRepository agents;
    @Autowired private DepartmentRepository departments;
    @Autowired private SlaPolicyRepository slaPolicies;
    @Autowired private EscalationRuleRepository escalationRules;
    @Autowired private TicketActivityRepository activities;
    @Autowired private ChatSessionService chats;
    @Autowired private SlaScheduler slaScheduler;

    @Autowired
    @Qualifier(EscalatedTransactionManagers.ESCALATED)
    private PlatformTransactionManager transactionManager;

    @MockitoBean private WebhookService webhookService;

    private String unique;
    private AgentProfile admin;
    private final List<Long> createdWorkflows = new ArrayList<>();

    @AfterEach
    void removeWorkflows() {
        // Workflows are global: leaving them would tag other tests' tickets.
        // Runs may still be finishing on another thread.
        await().pollDelay(Duration.ofMillis(300)).atMost(PATIENCE).until(() -> true);
        inTransaction(() -> {
            workflowLogs.findAll().stream()
                    .filter(row -> row.getWorkflow() != null && createdWorkflows.contains(row.getWorkflow().getId()))
                    .forEach(workflowLogs::delete);
            createdWorkflows.forEach(workflows::deleteById);
            return null;
        });
    }

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        admin = staff("Admin " + unique, true);
    }

    @Test
    void unassigningIsTicketUnassignedNotTicketAssigned() throws Exception {
        Ticket ticket = newTicket();
        AgentProfile agent = staff("Agent " + unique, false);
        ticketService.assign(ticket.getId(), agent.getId(), "system");
        verify(webhookService, timeout(WAIT_MS)).dispatchEvent("ticket.assigned", ticket.getId());

        mockMvc.perform(post("/escalated/api/admin/tickets/{id}/assign", ticket.getId())
                        .with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agent_id\":null}"))
                .andExpect(status().isOk());

        verify(webhookService, timeout(WAIT_MS)).dispatchEvent("ticket.unassigned", ticket.getId());
        verify(webhookService, after(500).times(1)).dispatchEvent("ticket.assigned", ticket.getId());
    }

    @Test
    void changingTheDepartmentIsAnnouncedAndRunsItsWorkflows() throws Exception {
        Department department = new Department();
        department.setName("Billing " + unique);
        department = departments.save(department);
        String tagName = "moved-" + unique;
        workflow("ticket.department_changed", tagName);
        Ticket ticket = newTicket();

        mockMvc.perform(post("/escalated/api/admin/tickets/{id}/department", ticket.getId())
                        .with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department_id\":" + department.getId() + "}"))
                .andExpect(status().isOk());

        verify(webhookService, timeout(WAIT_MS))
                .dispatchEvent(eq("ticket.department_changed"), eq(ticket.getId()), anyMap());
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(tagNamesOf(ticket.getId())).contains(tagName));
    }

    @Test
    void tagsAreAddedAndRemovedUnderTheirCanonicalNames() throws Exception {
        String marker = "legacy-tagged-" + unique;
        workflow("ticket.tagged", marker);
        Ticket ticket = newTicket();
        String tagName = "vip-" + unique;

        mockMvc.perform(post("/escalated/api/admin/tickets/{id}/tags", ticket.getId())
                        .with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"" + tagName + "\"}"))
                .andExpect(status().isOk());

        verify(webhookService, timeout(WAIT_MS))
                .dispatchEvent(eq("ticket.tag_added"), eq(ticket.getId()), argThat(data -> tagName.equals(data.get("tag"))));
        // A workflow saved under the legacy name still runs.
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(tagNamesOf(ticket.getId())).contains(marker));

        Long tagId = tags.findByName(tagName).orElseThrow().getId();
        mockMvc.perform(delete("/escalated/api/admin/tickets/{id}/tags/{tagId}", ticket.getId(), tagId)
                        .with(user(admin.getEmail())))
                .andExpect(status().isOk());

        verify(webhookService, timeout(WAIT_MS))
                .dispatchEvent(eq("ticket.tag_removed"), eq(ticket.getId()), anyMap());
        assertThat(tagNamesOf(ticket.getId())).doesNotContain(tagName);
    }

    @Test
    void anInternalNoteIsNoteCreatedAndALegacyRepliedWorkflowStillRuns() throws Exception {
        String repliedMarker = "replied-" + unique;
        String noteMarker = "noted-" + unique;
        workflow("ticket.replied", repliedMarker);
        workflow("internal_note.added", noteMarker);
        Ticket ticket = newTicket();
        AgentProfile agent = staff("Agent " + unique, false);

        mockMvc.perform(post("/escalated/api/agent/tickets/{id}/replies", ticket.getId())
                        .with(user(agent.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Checked the logs\",\"internal\":true}"))
                .andExpect(status().isCreated());

        verify(webhookService, timeout(WAIT_MS)).dispatchEvent(eq("note.created"), any());
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(tagNamesOf(ticket.getId())).contains(noteMarker));
        assertThat(tagNamesOf(ticket.getId())).doesNotContain(repliedMarker);

        mockMvc.perform(post("/escalated/api/agent/tickets/{id}/replies", ticket.getId())
                        .with(user(agent.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Fixed it\"}"))
                .andExpect(status().isCreated());

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(tagNamesOf(ticket.getId())).contains(repliedMarker));
    }

    @Test
    void anSlaBreachIsAnnouncedOnceAndRunsItsWorkflows() {
        String marker = "breached-" + unique;
        workflow("sla.breached", marker);
        Ticket ticket = newTicket();
        overdue(ticket, Instant.now().minusSeconds(600), null);

        slaScheduler.checkSlaBreaches();
        slaScheduler.checkSlaBreaches();

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(tagNamesOf(ticket.getId())).contains(marker));
        verify(webhookService, after(1000).times(1)).dispatchEvent(eq("sla.breached"), eq(ticket.getId()),
                argThat(data -> "resolution".equals(data.get("sla_type"))));
    }

    @Test
    void anSlaWarningIsAnnouncedOnce() {
        String marker = "warned-" + unique;
        workflow("sla.warning", marker);
        Ticket ticket = newTicket();
        overdue(ticket, Instant.now().plusSeconds(600), null);

        slaScheduler.checkSlaBreaches();
        slaScheduler.checkSlaBreaches();

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(tagNamesOf(ticket.getId())).contains(marker));
        verify(webhookService, after(1000).times(1)).dispatchEvent(eq("sla.warning"), eq(ticket.getId()), anyMap());
    }

    @Test
    void anEscalationRuleActsOnceNotEveryMinute() {
        AgentProfile agent = staff("Escalation target " + unique, false);
        SlaPolicy policy = new SlaPolicy();
        policy.setName("Escalation policy " + unique);
        policy.setPriority(TicketPriority.LOW);
        policy.setFirstResponseMinutes(60);
        policy.setResolutionMinutes(120);
        // Inactive, so no other test's ticket is put on it.
        policy.setActive(false);
        policy = slaPolicies.save(policy);
        EscalationRule rule = new EscalationRule();
        rule.setName("Reassign overdue " + unique);
        rule.setSlaPolicy(policy);
        rule.setActionType("reassign");
        rule.setActionTarget(String.valueOf(agent.getId()));
        escalationRules.save(rule);
        Ticket ticket = newTicket();
        overdue(ticket, Instant.now().minusSeconds(600), policy);

        slaScheduler.checkSlaBreaches();
        slaScheduler.checkSlaBreaches();
        slaScheduler.checkSlaBreaches();

        verify(webhookService, timeout(WAIT_MS)).dispatchEvent(eq("ticket.escalated"), eq(ticket.getId()), anyMap());
        verify(webhookService, after(1000).times(1))
                .dispatchEvent(eq("ticket.escalated"), eq(ticket.getId()), anyMap());
        verify(webhookService, times(1)).dispatchEvent("ticket.assigned", ticket.getId());
        assertThat(activities.findByTicketIdOrderByCreatedAtDesc(ticket.getId()))
                .extracting(TicketActivity::getType)
                .filteredOn("assigned"::equals)
                .hasSize(1);
    }

    @Test
    void chatLifecycleEventsReachWebhooksAndWorkflows() throws Exception {
        String marker = "chat-started-" + unique;
        workflow("chat.session_started", marker);
        AgentProfile agent = staff("Chat agent " + unique, false);
        Long sessionId = chats.startForWidget("Visitor", "visitor-" + unique + "@example.com", "Hi", null, null)
                .session().getId();
        Long ticketId = chats.findById(sessionId).getTicketId();

        verify(webhookService, timeout(WAIT_MS)).dispatchEvent(eq("chat.started"), eq(ticketId),
                argThat(data -> sessionId.equals(data.get("chat_session_id")) && !data.containsKey("token")));
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(tagNamesOf(ticketId)).contains(marker));

        mockMvc.perform(post("/escalated/api/agent/chat/{id}/accept", sessionId).with(user(agent.getEmail())))
                .andExpect(status().isOk());
        verify(webhookService, timeout(WAIT_MS)).dispatchEvent(eq("chat.assigned"), eq(ticketId), anyMap());

        mockMvc.perform(post("/escalated/api/agent/chat/{id}/messages", sessionId)
                        .with(user(agent.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"How can I help?\"}"))
                .andExpect(status().isCreated());
        verify(webhookService, timeout(WAIT_MS)).dispatchEvent(eq("chat.message"), eq(ticketId), anyMap());

        mockMvc.perform(post("/escalated/api/agent/chat/{id}/end", sessionId).with(user(agent.getEmail())))
                .andExpect(status().isOk());
        verify(webhookService, timeout(WAIT_MS)).dispatchEvent(eq("chat.ended"), eq(ticketId), anyMap());
    }

    @Test
    void resolvingIsAnnouncedAsAStatusChangeAndAsResolved() {
        String marker = "resolved-" + unique;
        workflow("ticket.resolved", marker);
        Ticket ticket = newTicket();

        ticketService.changeStatus(ticket.getId(), dev.escalated.models.TicketStatus.RESOLVED, "system");

        verify(webhookService, timeout(WAIT_MS)).dispatchEvent("ticket.resolved", ticket.getId());
        verify(webhookService, timeout(WAIT_MS)).dispatchEvent("ticket.status_changed", ticket.getId());
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(tagNamesOf(ticket.getId())).contains(marker));
    }

    @Test
    void theWorkflowBuilderIsOfferedOnlyCanonicalNames() {
        assertThat(WorkflowEngine.TRIGGER_EVENTS)
                .contains("ticket.tag_added", "ticket.tag_removed", "ticket.unassigned", "ticket.escalated",
                        "note.created", "sla.warning", "sla.breached", "chat.started", "ticket.department_changed")
                .doesNotContainAnyElementsOf(EventNames.LEGACY.keySet())
                .doesNotContain("reply.agent_reply");
        verify(webhookService, never()).dispatchEvent(eq("ticket.tagged"), any());
    }

    private Ticket newTicket() {
        return ticketService.create("Events " + unique, "Body", "Alice",
                "alice-" + unique + "@example.com", TicketPriority.LOW, null);
    }

    private void overdue(Ticket ticket, Instant due, SlaPolicy policy) {
        inTransaction(() -> {
            Ticket fresh = tickets.findById(ticket.getId()).orElseThrow();
            fresh.setSlaDueAt(due);
            if (policy != null) {
                fresh.setSlaPolicy(policy);
            }
            return tickets.save(fresh);
        });
    }

    private void workflow(String trigger, String tagName) {
        Tag tag = new Tag();
        tag.setName(tagName);
        tags.save(tag);
        Workflow workflow = new Workflow();
        workflow.setName("Tag on " + trigger + " " + unique);
        workflow.setTriggerEvent(trigger);
        workflow.setActions("[{\"type\":\"add_tag\",\"value\":\"" + tagName + "\"}]");
        workflow.setActive(true);
        createdWorkflows.add(workflows.save(workflow).getId());
    }

    private AgentProfile staff(String name, boolean isAdmin) {
        AgentProfile profile = new AgentProfile();
        profile.setName(name);
        profile.setEmail("staff-" + UUID.randomUUID() + "@example.com");
        profile.setAgent(true);
        profile.setAdmin(isAdmin);
        return agents.save(profile);
    }

    private List<String> tagNamesOf(Long ticketId) {
        return inTransaction(() -> tickets.findById(ticketId).orElseThrow().getTags().stream()
                .map(Tag::getName)
                .toList());
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
