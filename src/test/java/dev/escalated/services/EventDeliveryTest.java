package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.Workflow;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.repositories.WorkflowLogRepository;
import dev.escalated.repositories.WorkflowRepository;
import hostapp.HostApplication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What happens after a ticket or reply is saved: webhooks and workflows.
 *
 * <p>Both listeners are {@code @Async}, which does nothing without
 * {@code @EnableAsync} — and nothing enabled it. So a webhook's HTTP call ran on
 * the request thread inside the ticket's transaction, and a failure there rolled
 * the ticket back. Replies published no event at all, so a {@code reply.created}
 * workflow never ran.
 *
 * <p>The listener tests in the suite call the listeners directly and never
 * publish an event, so none of this was visible to them.
 */
@SpringBootTest(classes = HostApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.mail.host=localhost"})
class EventDeliveryTest {

    private static final Duration PATIENCE = Duration.ofSeconds(10);

    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository tickets;
    @Autowired private TagRepository tags;
    @Autowired private WorkflowRepository workflows;
    @Autowired private WorkflowLogRepository workflowLogs;

    @Autowired
    @Qualifier(EscalatedTransactionManagers.ESCALATED)
    private PlatformTransactionManager transactionManager;

    @MockitoBean private WebhookService webhookService;

    private final String unique = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> createdTickets = new ArrayList<>();
    private final List<Long> createdWorkflows = new ArrayList<>();
    private final List<Long> createdTags = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // Workflows may still be running on another thread.
        await().atMost(PATIENCE).pollDelay(Duration.ofMillis(200)).until(() -> true);

        inTransaction(() -> {
            workflowLogs.findAll().stream()
                    .filter(row -> createdTickets.contains(row.getTicket().getId()))
                    .forEach(workflowLogs::delete);
            createdTickets.forEach(tickets::deleteById);
            createdWorkflows.forEach(workflows::deleteById);
            createdTags.forEach(tags::deleteById);
            return null;
        });
    }

    @Test
    void aFailingWebhookDoesNotUndoTheTicket() {
        doThrow(new RuntimeException("webhook endpoint is down"))
                .when(webhookService).dispatchEvent(anyString(), any());

        Ticket ticket = newTicket();

        assertThat(tickets.findById(ticket.getId())).isPresent();
    }

    @Test
    void webhooksAreDispatchedOffTheCallersThread() {
        AtomicReference<Thread> dispatchedOn = new AtomicReference<>();
        doAnswer(invocation -> {
            dispatchedOn.set(Thread.currentThread());
            return null;
        }).when(webhookService).dispatchEvent(eq("ticket.created"), any());

        newTicket();

        await().atMost(PATIENCE).until(() -> dispatchedOn.get() != null);
        assertThat(dispatchedOn.get()).isNotSameAs(Thread.currentThread());
    }

    @Test
    void webhooksAreDispatchedOnlyOnceTheTicketIsCommitted() {
        AtomicReference<Boolean> visibleToOthers = new AtomicReference<>();
        doAnswer(invocation -> {
            Long ticketId = invocation.getArgument(1);
            // Another thread, so another connection: it sees only what has
            // been committed. A receiver fetching the ticket it was just told
            // about must find it.
            visibleToOthers.set(CompletableFuture.supplyAsync(() -> tickets.findById(ticketId).isPresent()).get());
            return null;
        }).when(webhookService).dispatchEvent(eq("ticket.created"), any());

        newTicket();

        await().atMost(PATIENCE).until(() -> visibleToOthers.get() != null);
        assertThat(visibleToOthers.get()).isTrue();
    }

    @Test
    void aReplyCreatedWorkflowRunsWhenAReplyIsAdded() {
        String tagName = tag("replied");
        workflow("reply.created", tagName);
        Ticket ticket = newTicket();

        ticketService.addReply(ticket.getId(), "Have you tried turning it off?", "Agent", "agent@example.com",
                "agent", false);

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(tagNamesOf(ticket.getId())).contains(tagName));
    }

    @Test
    void aTicketCreatedWorkflowStillRuns() {
        String tagName = tag("new");
        workflow("ticket.created", tagName);

        Ticket ticket = newTicket();

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(tagNamesOf(ticket.getId())).contains(tagName));
    }

    private Ticket newTicket() {
        Ticket ticket = ticketService.create("Printer on fire " + unique, "It is on fire", "Alice",
                "alice-" + unique + "@example.com", TicketPriority.HIGH, null);
        createdTickets.add(ticket.getId());
        return ticket;
    }

    private String tag(String prefix) {
        Tag tag = new Tag();
        tag.setName(prefix + "-" + unique);
        createdTags.add(tags.save(tag).getId());
        return tag.getName();
    }

    private void workflow(String trigger, String tagName) {
        Workflow workflow = new Workflow();
        workflow.setName("Tag on " + trigger + " " + unique);
        workflow.setTriggerEvent(trigger);
        workflow.setActions("[{\"type\":\"add_tag\",\"value\":\"" + tagName + "\"}]");
        workflow.setActive(true);
        createdWorkflows.add(workflows.save(workflow).getId());
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
