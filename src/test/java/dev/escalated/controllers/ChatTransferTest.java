package dev.escalated.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.events.EventNames;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.ChatRoutingRule;
import dev.escalated.models.ChatSession;
import dev.escalated.models.Department;
import dev.escalated.models.Ticket;
import dev.escalated.models.Workflow;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ChatRoutingRuleRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.repositories.WorkflowLogRepository;
import dev.escalated.repositories.WorkflowRepository;
import dev.escalated.services.ChatSessionService;
import dev.escalated.services.WebhookService;
import dev.escalated.services.WorkflowEngine;
import dev.escalated.ui.UiRoutes;
import hostapp.HostApplication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Live chat transfer, as escalated-laravel's ChatController::transfer and
 * ChatSessionService::transferChat: {@code escalated.admin.chat.transfer}
 * takes the chat session id (the shared Agent/TicketShow page passes
 * {@code chat_session_id}) and {@code {agent_id, department_id}}; the shared
 * ChatActionBar's {@code {type, id}} is read too. It emits
 * {@code chat.transferred} to webhooks and workflows.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class ChatTransferTest {

    private static final long WAIT_MS = 10_000;

    @Autowired private MockMvc mockMvc;
    @Autowired private ChatSessionService chats;
    @Autowired private AgentProfileRepository agents;
    @Autowired private DepartmentRepository departments;
    @Autowired private ChatRoutingRuleRepository routingRules;
    @Autowired private TicketRepository tickets;
    @Autowired private WorkflowRepository workflows;
    @Autowired private WorkflowLogRepository workflowLogs;

    @Autowired
    @Qualifier(EscalatedTransactionManagers.ESCALATED)
    private PlatformTransactionManager transactionManager;

    @MockitoBean private WebhookService webhookService;

    private String unique;
    private AgentProfile from;
    private AgentProfile to;
    private final List<Long> createdRules = new ArrayList<>();
    private final List<Long> createdWorkflows = new ArrayList<>();

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        from = staff("From " + unique, true, false);
        to = staff("To " + unique, true, false);
    }

    @AfterEach
    void cleanUp() {
        // Routing rules and workflows are global: leaving them would route or
        // tag other tests' chats.
        await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(10)).until(() -> true);
        createdRules.forEach(routingRules::deleteById);
        inTransaction(() -> {
            workflowLogs.findAll().stream()
                    .filter(row -> row.getWorkflow() != null && createdWorkflows.contains(row.getWorkflow().getId()))
                    .forEach(workflowLogs::delete);
            createdWorkflows.forEach(workflows::deleteById);
            return null;
        });
    }

    @Test
    void theRouteHasLaravelsName() {
        assertThat(new UiRoutes().all())
                .containsEntry("escalated.admin.chat.transfer", "/escalated/api/agent/chat/{session}/transfer");
    }

    @Test
    void transferToAnAgentReassignsTheChatAndTicket() throws Exception {
        ChatSession session = activeChat();
        String marker = "transferred-" + unique;
        workflow(marker);

        transfer(session.getId(), "{\"agent_id\":" + to.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Chat transferred."))
                .andExpect(jsonPath("$.session.agentId").value(to.getId()));

        ChatSession fresh = chats.findById(session.getId());
        assertThat(fresh.getAgentId()).isEqualTo(to.getId());
        assertThat(fresh.getStatus()).isEqualTo("active");
        assertThat(assignedAgentId(session.getTicketId())).isEqualTo(to.getId());

        verify(webhookService, timeout(WAIT_MS)).dispatchEvent(eq("chat.transferred"), eq(session.getTicketId()),
                argThat(data -> session.getId().equals(data.get("chat_session_id"))
                        && from.getId().equals(data.get("from_agent_id"))
                        && to.getId().equals(data.get("to_agent_id"))
                        && data.containsKey("to_department_id") && data.get("to_department_id") == null
                        && !data.containsKey("token")));
        await().atMost(Duration.ofSeconds(10)).until(() -> inTransaction(() -> workflowLogs.findAll().stream()
                .anyMatch(row -> row.getWorkflow() != null
                        && createdWorkflows.contains(row.getWorkflow().getId())
                        && row.getTicket() != null && Objects.equals(row.getTicket().getId(), session.getTicketId()))));
    }

    @Test
    void departmentOnlyTransferRequeuesAndReroutes() throws Exception {
        Department billing = department();
        AgentProfile routed = staff("Routed " + unique, true, false);
        ChatSession session = activeChat();
        ChatRoutingRule rule = new ChatRoutingRule();
        rule.setName("Billing " + unique);
        rule.setDepartmentId(billing.getId());
        rule.setAgentId(routed.getId());
        rule.setActive(true);
        createdRules.add(routingRules.save(rule).getId());

        transfer(session.getId(), "{\"department_id\":" + billing.getId() + "}").andExpect(status().isOk());

        ChatSession fresh = chats.findById(session.getId());
        assertThat(fresh.getDepartmentId()).isEqualTo(billing.getId());
        assertThat(fresh.getAgentId()).isEqualTo(routed.getId());
        assertThat(fresh.getStatus()).isEqualTo("active");
        assertThat(departmentId(session.getTicketId())).isEqualTo(billing.getId());
        assertThat(assignedAgentId(session.getTicketId())).isEqualTo(routed.getId());

        verify(webhookService, timeout(WAIT_MS)).dispatchEvent(eq("chat.transferred"), eq(session.getTicketId()),
                argThat(data -> from.getId().equals(data.get("from_agent_id"))
                        && data.get("to_agent_id") == null
                        && billing.getId().equals(data.get("to_department_id"))));
    }

    @Test
    void departmentOnlyTransferWithNoAgentLeavesTheChatWaiting() throws Exception {
        Department billing = department();
        ChatSession session = activeChat();

        transfer(session.getId(), "{\"department_id\":\"" + billing.getId() + "\"}").andExpect(status().isOk());

        ChatSession fresh = chats.findById(session.getId());
        assertThat(fresh.getAgentId()).isNull();
        assertThat(fresh.getStatus()).isEqualTo("waiting");
        assertThat(fresh.getAcceptedAt()).isNull();
        assertThat(assignedAgentId(session.getTicketId())).isNull();
        assertThat(departmentId(session.getTicketId())).isEqualTo(billing.getId());
    }

    @Test
    void agentAndDepartmentTogether() throws Exception {
        Department billing = department();
        ChatSession session = activeChat();

        transfer(session.getId(), "{\"agent_id\":\"" + to.getId() + "\",\"department_id\":" + billing.getId() + "}")
                .andExpect(status().isOk());

        ChatSession fresh = chats.findById(session.getId());
        assertThat(fresh.getAgentId()).isEqualTo(to.getId());
        assertThat(fresh.getStatus()).isEqualTo("active");
        assertThat(departmentId(session.getTicketId())).isEqualTo(billing.getId());
    }

    @Test
    void theChatActionBarBodyIsAccepted() throws Exception {
        Department billing = department();
        ChatSession session = activeChat();

        transfer(session.getId(), "{\"type\":\"agent\",\"id\":" + to.getId() + "}").andExpect(status().isOk());
        assertThat(chats.findById(session.getId()).getAgentId()).isEqualTo(to.getId());

        transfer(session.getId(), "{\"type\":\"department\",\"id\":" + billing.getId() + "}")
                .andExpect(status().isOk());
        assertThat(departmentId(session.getTicketId())).isEqualTo(billing.getId());
    }

    @Test
    void refusals() throws Exception {
        ChatSession session = activeChat();
        AgentProfile notStaff = staff("Customer " + unique, false, false);
        AgentProfile inactive = staff("Gone " + unique, true, false);
        inactive.setActive(false);
        agents.save(inactive);

        mockMvc.perform(post("/escalated/api/agent/chat/{id}/transfer", session.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"agent_id\":" + to.getId() + "}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));

        transfer(session.getId(), "{}").andExpect(status().isUnprocessableContent());
        transfer(session.getId(), "{\"agent_id\":null,\"department_id\":\"\"}").andExpect(status().isUnprocessableContent());
        transfer(session.getId(), "{\"department_id\":99999999}").andExpect(status().isUnprocessableContent());
        transfer(session.getId(), "{\"department_id\":\"billing\"}").andExpect(status().isUnprocessableContent());
        transfer(session.getId(), "{\"agent_id\":99999999}").andExpect(status().isUnprocessableContent());
        transfer(session.getId(), "{\"agent_id\":" + notStaff.getId() + "}").andExpect(status().isUnprocessableContent());
        transfer(session.getId(), "{\"agent_id\":" + inactive.getId() + "}").andExpect(status().isUnprocessableContent());
        transfer(99999999L, "{\"agent_id\":" + to.getId() + "}").andExpect(status().isNotFound());

        chats.end(session.getId());
        transfer(session.getId(), "{\"agent_id\":" + to.getId() + "}").andExpect(status().isUnprocessableContent());

        assertThat(chats.findById(session.getId()).getAgentId()).isEqualTo(from.getId());
        verify(webhookService, after(1000).never()).dispatchEvent(eq("chat.transferred"), anyLong(), anyMap());
    }

    @Test
    void chatTransferredIsOfferedToWebhooksAndWorkflows() {
        assertThat(EventNames.EMITTED).contains("chat.transferred");
        assertThat(WorkflowEngine.TRIGGER_EVENTS).contains("chat.transferred");
    }

    private ResultActions transfer(Long sessionId, String body) throws Exception {
        return mockMvc.perform(post("/escalated/api/agent/chat/{id}/transfer", sessionId)
                .with(user(from.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ChatSession activeChat() throws Exception {
        Long sessionId = chats.startForWidget("Visitor", "visitor-" + unique + "@example.com", "Hi", null, null)
                .session().getId();
        if ("waiting".equals(chats.findById(sessionId).getStatus())) {
            chats.accept(sessionId, from.getId());
        }
        return chats.findById(sessionId);
    }

    private Department department() {
        Department department = new Department();
        department.setName("Billing " + unique + " " + UUID.randomUUID().toString().substring(0, 4));
        return departments.save(department);
    }

    private void workflow(String tagName) {
        Workflow workflow = new Workflow();
        workflow.setName("On transfer " + unique);
        workflow.setTriggerEvent("chat.transferred");
        workflow.setActions("[{\"type\":\"add_tag\",\"value\":\"" + tagName + "\"}]");
        workflow.setActive(true);
        createdWorkflows.add(workflows.save(workflow).getId());
    }

    private AgentProfile staff(String name, boolean agent, boolean admin) {
        AgentProfile profile = new AgentProfile();
        profile.setName(name);
        profile.setEmail("staff-" + UUID.randomUUID() + "@example.com");
        profile.setAgent(agent);
        profile.setAdmin(admin);
        return agents.save(profile);
    }

    private Long assignedAgentId(Long ticketId) {
        return inTransaction(() -> {
            Ticket ticket = tickets.findById(ticketId).orElseThrow();
            return ticket.getAssignedAgent() == null ? null : ticket.getAssignedAgent().getId();
        });
    }

    private Long departmentId(Long ticketId) {
        return inTransaction(() -> {
            Ticket ticket = tickets.findById(ticketId).orElseThrow();
            return ticket.getDepartment() == null ? null : ticket.getDepartment().getId();
        });
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
