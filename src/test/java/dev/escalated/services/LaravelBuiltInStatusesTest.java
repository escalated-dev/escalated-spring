package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.CustomTicketStatus;
import dev.escalated.models.EscalationRule;
import dev.escalated.models.SlaPolicy;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.CustomTicketStatusRepository;
import dev.escalated.repositories.EscalationRuleRepository;
import dev.escalated.repositories.SlaPolicyRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.scheduling.SlaScheduler;
import hostapp.HostApplication;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * escalated-laravel's built-in {@code escalated}, {@code reopened} and {@code live} statuses, set
 * where Laravel sets them: an escalation rule's {@code escalate} action escalates the ticket, a
 * reopen reopens it, and a live chat's ticket is live. Each is a status filters, workflow and
 * escalation conditions, and the status pages know.
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
class LaravelBuiltInStatusesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository tickets;
    @Autowired private AgentProfileRepository agents;
    @Autowired private SlaPolicyRepository slaPolicies;
    @Autowired private EscalationRuleRepository escalationRules;
    @Autowired private CustomTicketStatusRepository statusRows;
    @Autowired private TicketStatuses ticketStatuses;
    @Autowired private ChatSessionService chats;
    @Autowired private SlaScheduler slaScheduler;

    @Autowired
    @Qualifier(EscalatedTransactionManagers.ESCALATED)
    private PlatformTransactionManager transactionManager;

    @MockitoBean private WebhookService webhookService;

    private String unique;
    private AgentProfile agent;
    private final List<Long> rules = new ArrayList<>();

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        agent = new AgentProfile();
        agent.setName("Agent " + unique);
        agent.setEmail("agent-" + unique + "@example.com");
        agent.setAgent(true);
        agent.setAdmin(true);
        agent = agents.save(agent);
    }

    @AfterEach
    void removeRules() {
        // Rules are global: leaving them would act on other tests' tickets.
        rules.forEach(escalationRules::deleteById);
    }

    @Test
    void theStatusesAreBuiltInWithLaravelsValues() {
        assertThat(TicketStatuses.builtIn("escalated")).isEqualTo(TicketStatus.ESCALATED);
        assertThat(TicketStatuses.builtIn("reopened")).isEqualTo(TicketStatus.REOPENED);
        assertThat(TicketStatuses.builtIn("live")).isEqualTo(TicketStatus.LIVE);
        assertThat(TicketStatuses.categoryOf(TicketStatus.ESCALATED)).isEqualTo("open");
        assertThat(TicketStatuses.categoryOf(TicketStatus.REOPENED)).isEqualTo("open");
        assertThat(TicketStatuses.categoryOf(TicketStatus.LIVE)).isEqualTo("open");

        ticketStatuses.ensureSystemStatuses();
        assertThat(statusRows.findAll()).filteredOn(CustomTicketStatus::isSystem)
                .extracting(CustomTicketStatus::getSlug)
                .contains("escalated", "reopened", "live");
    }

    @Test
    void anEscalateActionEscalatesTheTicket() {
        SlaPolicy policy = inactivePolicy();
        rule(policy, "[{\"type\":\"escalate\"}]");
        Ticket ticket = ticket(t -> { });
        overdue(ticket, policy);

        slaScheduler.checkSlaBreaches();

        assertThat(statusOf(ticket)).isEqualTo(TicketStatus.ESCALATED);
        verify(webhookService, timeout(10_000)).dispatchEvent("ticket.status_changed", ticket.getId());
    }

    @Test
    void anEscalateActionLeavesATicketLaravelCannotEscalate() {
        SlaPolicy policy = inactivePolicy();
        rule(policy, "[{\"type\":\"escalate\"}]");
        Ticket waiting = ticket(t -> t.setStatus(TicketStatus.WAITING_ON_CUSTOMER));
        Ticket live = ticket(t -> t.setStatus(TicketStatus.LIVE));
        overdue(waiting, policy);
        overdue(live, policy);

        slaScheduler.checkSlaBreaches();

        assertThat(statusOf(waiting)).isEqualTo(TicketStatus.WAITING_ON_CUSTOMER);
        assertThat(statusOf(live)).isEqualTo(TicketStatus.LIVE);
    }

    @Test
    void reopeningIsTheReopenedStatusAndClearsTheResolvedAndClosedTimes() {
        Ticket ticket = ticket(t -> { });
        ticketService.changeStatus(ticket.getId(), TicketStatus.RESOLVED, "system");

        Ticket reopened = ticketService.changeStatusByName(ticket.getId(), "reopened", "system");

        assertThat(reopened.getStatus()).isEqualTo(TicketStatus.REOPENED);
        Ticket stored = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(stored.getResolvedAt()).isNull();
        assertThat(stored.getClosedAt()).isNull();
        verify(webhookService, timeout(10_000)).dispatchEvent("ticket.reopened", ticket.getId());
    }

    @Test
    void aLiveChatsTicketIsLive() {
        Long sessionId = chats.startForWidget("Visitor", "visitor-" + unique + "@example.com", "Hi", null, null)
                .session().getId();
        Long ticketId = chats.findById(sessionId).getTicketId();

        assertThat(tickets.findById(ticketId).orElseThrow().getStatus()).isEqualTo(TicketStatus.LIVE);
    }

    @Test
    void theAgentListFiltersOnEachStatus() throws Exception {
        Ticket escalated = ticket(t -> t.setStatus(TicketStatus.ESCALATED));
        Ticket reopened = ticket(t -> t.setStatus(TicketStatus.REOPENED));
        Ticket live = ticket(t -> t.setStatus(TicketStatus.LIVE));
        ticket(t -> { });

        assertThat(ids("escalated")).containsExactly(escalated.getId());
        assertThat(ids("reopened")).containsExactly(reopened.getId());
        assertThat(ids("live")).containsExactly(live.getId());
        JsonNode row = list("escalated").get(0);
        assertThat(row.get("status").asText()).isEqualTo("escalated");
    }

    @Test
    void workflowAndEscalationConditionsMatchEachStatus() {
        WorkflowEngine engine = new WorkflowEngine();
        Map<String, Object> isLive = Map.of("all", List.of(
                Map.of("field", "status", "operator", "equals", "value", "live")));
        assertThat(engine.evaluateConditions(isLive, Map.of("status", "live"))).isTrue();
        assertThat(engine.evaluateConditions(isLive, Map.of("status", "open"))).isFalse();

        Ticket ticket = new Ticket();
        ticket.setStatus(TicketStatus.REOPENED);
        assertThat(EscalationConditions.matches("[{\"field\":\"status\",\"value\":\"reopened\"}]", ticket, Instant.now()))
                .isTrue();
        assertThat(EscalationConditions.matches("[{\"field\":\"status\",\"value\":\"escalated\"}]", ticket, Instant.now()))
                .isFalse();
    }

    private List<Long> ids(String status) throws Exception {
        List<Long> ids = new ArrayList<>();
        list(status).forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }

    private JsonNode list(String status) throws Exception {
        String body = mvc.perform(get("/escalated/agent/tickets?status=" + status + "&search=" + unique)
                        .with(user(agent.getEmail()))
                        .header("X-Inertia", "true").header("X-Inertia-Version", "build-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("props").get("tickets").get("data");
    }

    private Ticket ticket(Consumer<Ticket> change) {
        Ticket ticket = ticketService.create("Statuses " + unique, "Body", "Alice",
                "alice-" + unique + "@example.com", TicketPriority.LOW, null);
        return inTransaction(() -> {
            Ticket fresh = tickets.findById(ticket.getId()).orElseThrow();
            change.accept(fresh);
            return tickets.save(fresh);
        });
    }

    private SlaPolicy inactivePolicy() {
        SlaPolicy policy = new SlaPolicy();
        policy.setName("Statuses policy " + unique);
        policy.setFirstResponseHours(Map.of("low", 1));
        policy.setResolutionHours(Map.of("low", 2));
        // Inactive, so no other test's ticket is put on it.
        policy.setActive(false);
        return slaPolicies.save(policy);
    }

    private void rule(SlaPolicy policy, String actions) {
        EscalationRule rule = new EscalationRule();
        rule.setName("Escalate overdue " + unique);
        rule.setConditions("[{\"field\":\"sla_policy_id\",\"value\":\"" + policy.getId() + "\"},"
                + "{\"field\":\"sla_breached\",\"value\":\"resolution\"}]");
        rule.setActions(actions);
        rules.add(escalationRules.save(rule).getId());
    }

    private void overdue(Ticket ticket, SlaPolicy policy) {
        inTransaction(() -> {
            Ticket fresh = tickets.findById(ticket.getId()).orElseThrow();
            fresh.setSlaDueAt(Instant.now().minusSeconds(600));
            fresh.setSlaPolicy(policy);
            return tickets.save(fresh);
        });
    }

    private TicketStatus statusOf(Ticket ticket) {
        return tickets.findById(ticket.getId()).orElseThrow().getStatus();
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
