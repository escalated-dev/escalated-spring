package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.CustomTicketStatus;
import dev.escalated.models.Macro;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.CustomTicketStatusRepository;
import dev.escalated.repositories.MacroRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.scheduling.AutomationScheduler;
import hostapp.HostApplication;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
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
 * A custom status is a sub-status of a category, and the lifecycle keys off the category: the
 * ticket takes the category's built-in anchor through the normal status-change path.
 */
@SpringBootTest(classes = HostApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mail.host=localhost")
class CustomTicketStatusLifecycleTest {

    private static final long WAIT_MS = 10_000;

    @Autowired private MockMvc mockMvc;
    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository tickets;
    @Autowired private CustomTicketStatusRepository statuses;
    @Autowired private TicketStatuses ticketStatuses;
    @Autowired private SlaService slaService;
    @Autowired private AutomationScheduler automations;
    @Autowired private MacroService macroService;
    @Autowired private MacroRepository macros;
    @Autowired private AgentProfileRepository agents;

    @Autowired
    @Qualifier(EscalatedTransactionManagers.ESCALATED)
    private PlatformTransactionManager transactionManager;

    @MockitoBean private WebhookService webhookService;

    private String unique;

    @BeforeEach
    void seed() {
        unique = UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void aSolvedCustomStatusResolvesTheTicketAndStopsItsSla() {
        CustomTicketStatus fixed = custom("Fixed upstream", "solved");
        Ticket ticket = newTicket();
        inTransaction(() -> {
            Ticket fresh = tickets.findById(ticket.getId()).orElseThrow();
            fresh.setSlaDueAt(Instant.now().minus(Duration.ofHours(1)));
            return tickets.save(fresh);
        });
        assertThat(slaService.findBreachingSlaTickets()).extracting(Ticket::getId).contains(ticket.getId());

        ticketService.changeStatusByName(ticket.getId(), fixed.getSlug(), "agent@example.com");

        Ticket stored = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(stored.getCustomStatus().getId()).isEqualTo(fixed.getId());
        assertThat(stored.getResolvedAt()).isNotNull();
        assertThat(stored.getStatusCategory()).isEqualTo("solved");
        assertThat(slaService.findBreachingSlaTickets()).extracting(Ticket::getId).doesNotContain(ticket.getId());

        verify(webhookService, timeout(WAIT_MS)).dispatchEvent(eq("ticket.resolved"), eq(ticket.getId()),
                argThat(data -> fixed.getSlug().equals(data.get("new_custom_status"))
                        && "solved".equals(data.get("new_category")) && "open".equals(data.get("old_category"))
                        && data.containsKey("old_custom_status") && data.get("old_custom_status") == null));
        verify(webhookService, timeout(WAIT_MS)).dispatchEvent(eq("ticket.status_changed"), eq(ticket.getId()),
                anyMap());
    }

    @Test
    void aPendingCustomStatusIsWaitingOnTheCustomer() {
        CustomTicketStatus awaiting = custom("Awaiting logs", "pending");
        Ticket ticket = newTicket();

        ticketService.changeStatusByName(ticket.getId(), awaiting.getSlug(), "agent@example.com");

        Ticket stored = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TicketStatus.PENDING);
        assertThat(stored.getCustomStatus().getSlug()).isEqualTo(awaiting.getSlug());
    }

    @Test
    void settingABuiltInStatusClearsTheCustomStatus() {
        CustomTicketStatus parts = custom("Waiting on parts", "on_hold");
        Ticket ticket = newTicket();
        ticketService.changeStatusByName(ticket.getId(), parts.getSlug(), "agent@example.com");
        assertThat(tickets.findById(ticket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        // The same built-in status as the anchor: still a change, because the custom status goes.
        ticketService.changeStatus(ticket.getId(), TicketStatus.IN_PROGRESS, "agent@example.com");
        Ticket stored = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(stored.getCustomStatus()).isNull();

        ticketService.changeStatusByName(ticket.getId(), parts.getSlug(), "agent@example.com");
        ticketService.snooze(ticket.getId(), Instant.now().plus(Duration.ofDays(1)), "agent@example.com");
        stored = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TicketStatus.SNOOZED);
        assertThat(stored.getCustomStatus()).isNull();
    }

    @Test
    void aMoveBetweenSubStatusesOfOneCategoryIsAStatusChange() {
        CustomTicketStatus first = custom("Fixed in beta", "solved");
        CustomTicketStatus second = custom("Fixed in release", "solved");
        Ticket ticket = newTicket();
        ticketService.changeStatusByName(ticket.getId(), first.getSlug(), "agent@example.com");
        Instant resolvedAt = tickets.findById(ticket.getId()).orElseThrow().getResolvedAt();

        ticketService.changeStatusByName(ticket.getId(), second.getSlug(), "agent@example.com");

        Ticket stored = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(stored.getCustomStatus().getId()).isEqualTo(second.getId());
        assertThat(stored.getResolvedAt()).isEqualTo(resolvedAt);
        verify(webhookService, timeout(WAIT_MS)).dispatchEvent(eq("ticket.status_changed"), eq(ticket.getId()),
                argThat(data -> second.getSlug().equals(data.get("new_custom_status"))
                        && first.getSlug().equals(data.get("old_custom_status"))));
        // Resolved once, by the first move only.
        verify(webhookService, after(500).never()).dispatchEvent(eq("ticket.resolved"), eq(ticket.getId()),
                argThat(data -> second.getSlug().equals(data.get("new_custom_status"))));
    }

    @Test
    void autoCloseClosesATicketWithASolvedCustomStatus() {
        CustomTicketStatus fixed = custom("Shipped", "solved");
        Ticket ticket = newTicket();
        ticketService.changeStatusByName(ticket.getId(), fixed.getSlug(), "agent@example.com");
        inTransaction(() -> {
            Ticket fresh = tickets.findById(ticket.getId()).orElseThrow();
            fresh.setResolvedAt(Instant.now().minus(Duration.ofDays(8)));
            return tickets.save(fresh);
        });

        automations.autoCloseResolvedTickets();

        Ticket stored = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(stored.getCustomStatus()).isNull();
    }

    @Test
    void anUnknownStatusIsRefused() {
        Ticket ticket = newTicket();
        assertThatThrownBy(() -> ticketService.changeStatusByName(ticket.getId(), "nothing_" + unique, "a@b.c"))
                .isInstanceOf(IllegalArgumentException.class);
        // A system row's slug is the built-in status, never a custom one.
        ticketStatuses.ensureSystemStatuses();
        ticketService.changeStatusByName(ticket.getId(), "in_progress", "a@b.c");
        Ticket stored = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(stored.getCustomStatus()).isNull();
    }

    @Test
    void aMacroSetsACustomStatus() {
        CustomTicketStatus review = custom("In review", "open");
        Ticket ticket = newTicket();
        Macro macro = new Macro();
        macro.setName("Review " + unique);
        macro.setActions("[{\"type\":\"status\",\"value\":\"" + review.getSlug() + "\"}]");
        macro = macros.save(macro);

        macroService.applyMacro(macro.getId(), ticket.getId(), "agent@example.com");

        Ticket stored = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(stored.getCustomStatus().getId()).isEqualTo(review.getId());
    }

    @Test
    void theStaffApiTakesACustomSlugAndShowsItButTheCustomerApiDoesNot() throws Exception {
        CustomTicketStatus escalatedToVendor = custom("Escalated to vendor " + unique, "on_hold");
        AgentProfile admin = new AgentProfile();
        admin.setName("Admin " + unique);
        admin.setEmail("admin-" + unique + "@example.com");
        admin.setAgent(true);
        admin.setAdmin(true);
        admin = agents.save(admin);
        Ticket ticket = newTicket();

        mockMvc.perform(post("/escalated/api/admin/tickets/{id}/status", ticket.getId())
                        .with(user(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + escalatedToVendor.getSlug() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.status_category").value("on_hold"))
                .andExpect(jsonPath("$.custom_status.label").value(escalatedToVendor.getLabel()))
                .andExpect(jsonPath("$.custom_status.slug").value(escalatedToVendor.getSlug()))
                .andExpect(jsonPath("$.custom_status.color").value("#6b7280"))
                .andExpect(jsonPath("$.custom_status.category").value("on_hold"))
                .andExpect(jsonPath("$.custom_status.id").value(escalatedToVendor.getId()));

        String customerView = mockMvc.perform(get("/escalated/api/customer/tickets/{id}", ticket.getId())
                        .with(user(ticket.getRequesterEmail())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(customerView).doesNotContain(escalatedToVendor.getLabel())
                .doesNotContain(escalatedToVendor.getSlug()).doesNotContain("custom_status");
    }

    private CustomTicketStatus custom(String label, String category) {
        CustomTicketStatus status = new CustomTicketStatus();
        status.setLabel(label + " " + unique);
        status.setSlug(ticketStatuses.uniqueSlug(label + " " + unique));
        status.setCategory(category);
        return statuses.save(status);
    }

    private Ticket newTicket() {
        return ticketService.create("Statuses " + unique, "Body", "Alice",
                "alice-" + unique + "@example.com", TicketPriority.LOW, null);
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
