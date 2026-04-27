package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.DeferredWorkflowJob;
import dev.escalated.models.Department;
import dev.escalated.models.Reply;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DeferredWorkflowJobRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link WorkflowExecutorService}. Mocks every
 * repository — no Spring context. Mirrors the test coverage of the
 * NestJS reference {@code workflow-executor.service.ts} and covers
 * each action type in the catalog plus the malformed-input paths.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowExecutorServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TagRepository tagRepository;
    @Mock private AgentProfileRepository agentRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ReplyRepository replyRepository;
    @Mock private DeferredWorkflowJobRepository deferredRepository;

    private WorkflowExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = new WorkflowExecutorService(
                ticketRepository, tagRepository, agentRepository,
                departmentRepository, replyRepository, deferredRepository);
    }

    private Ticket newTicket() {
        Ticket t = new Ticket();
        t.setId(1L);
        t.setSubject("Help");
        t.setBody("body");
        t.setTicketNumber("ESC-00001");
        t.setRequesterName("Alice");
        t.setRequesterEmail("alice@example.com");
        t.setPriority(TicketPriority.LOW);
        t.setStatus(TicketStatus.OPEN);
        t.setTags(new HashSet<>());
        return t;
    }

    @Test
    void execute_changePriority_updatesTicket() {
        Ticket ticket = newTicket();

        executor.execute(ticket, "[{\"type\":\"change_priority\",\"value\":\"high\"}]");

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void execute_changeStatus_updatesTicket() {
        Ticket ticket = newTicket();

        executor.execute(ticket, "[{\"type\":\"change_status\",\"value\":\"resolved\"}]");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void execute_assignAgent_looksUpAndAssigns() {
        Ticket ticket = newTicket();
        AgentProfile agent = new AgentProfile();
        agent.setId(7L);

        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));

        executor.execute(ticket, "[{\"type\":\"assign_agent\",\"value\":\"7\"}]");

        assertThat(ticket.getAssignedAgent()).isSameAs(agent);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void execute_assignAgent_missingAgentDoesNotSave() {
        Ticket ticket = newTicket();
        when(agentRepository.findById(99L)).thenReturn(Optional.empty());

        executor.execute(ticket, "[{\"type\":\"assign_agent\",\"value\":\"99\"}]");

        assertThat(ticket.getAssignedAgent()).isNull();
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void execute_setDepartment_looksUpAndAssigns() {
        Ticket ticket = newTicket();
        Department dept = new Department();
        dept.setId(3L);

        when(departmentRepository.findById(3L)).thenReturn(Optional.of(dept));

        executor.execute(ticket, "[{\"type\":\"set_department\",\"value\":\"3\"}]");

        assertThat(ticket.getDepartment()).isSameAs(dept);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void execute_addTag_byName_addsToTicket() {
        Ticket ticket = newTicket();
        Tag tag = new Tag();
        tag.setId(5L);
        tag.setName("urgent");

        when(tagRepository.findByName("urgent")).thenReturn(Optional.of(tag));

        executor.execute(ticket, "[{\"type\":\"add_tag\",\"value\":\"urgent\"}]");

        assertThat(ticket.getTags()).contains(tag);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void execute_addTag_byId_fallsBackWhenNameMisses() {
        Ticket ticket = newTicket();
        Tag tag = new Tag();
        tag.setId(5L);
        tag.setName("urgent");

        when(tagRepository.findByName("5")).thenReturn(Optional.empty());
        when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));

        executor.execute(ticket, "[{\"type\":\"add_tag\",\"value\":\"5\"}]");

        assertThat(ticket.getTags()).contains(tag);
    }

    @Test
    void execute_addTag_unknownTagSkipped() {
        Ticket ticket = newTicket();
        when(tagRepository.findByName("missing")).thenReturn(Optional.empty());

        executor.execute(ticket, "[{\"type\":\"add_tag\",\"value\":\"missing\"}]");

        assertThat(ticket.getTags()).isEmpty();
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void execute_removeTag_removesFromTicket() {
        Ticket ticket = newTicket();
        Tag tag = new Tag();
        tag.setId(5L);
        tag.setName("urgent");
        ticket.getTags().add(tag);

        when(tagRepository.findByName("urgent")).thenReturn(Optional.of(tag));

        executor.execute(ticket, "[{\"type\":\"remove_tag\",\"value\":\"urgent\"}]");

        assertThat(ticket.getTags()).doesNotContain(tag);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void execute_addNote_persistsInternalReply() {
        Ticket ticket = newTicket();

        executor.execute(ticket, "[{\"type\":\"add_note\",\"value\":\"internal\"}]");

        ArgumentCaptor<Reply> captor = ArgumentCaptor.forClass(Reply.class);
        verify(replyRepository).save(captor.capture());
        Reply saved = captor.getValue();
        assertThat(saved.getBody()).isEqualTo("internal");
        assertThat(saved.isInternal()).isTrue();
        assertThat(saved.getTicket()).isSameAs(ticket);
    }

    @Test
    void execute_addNote_blankSkipped() {
        Ticket ticket = newTicket();

        executor.execute(ticket, "[{\"type\":\"add_note\",\"value\":\"  \"}]");

        verify(replyRepository, never()).save(any());
    }

    @Test
    void execute_insertCannedReply_interpolatesAndSavesPublicReply() {
        Ticket ticket = newTicket();

        executor.execute(ticket,
                "[{\"type\":\"insert_canned_reply\",\"value\":\"Hi {{requester_name}}, ref {{ticket_number}}\"}]");

        ArgumentCaptor<Reply> captor = ArgumentCaptor.forClass(Reply.class);
        verify(replyRepository).save(captor.capture());
        Reply saved = captor.getValue();
        assertThat(saved.getBody()).isEqualTo("Hi Alice, ref ESC-00001");
        assertThat(saved.isInternal()).isFalse();
    }

    @Test
    void execute_insertCannedReply_unknownVariableLeftLiteral() {
        Ticket ticket = newTicket();

        executor.execute(ticket,
                "[{\"type\":\"insert_canned_reply\",\"value\":\"Hi {{unknown}}\"}]");

        ArgumentCaptor<Reply> captor = ArgumentCaptor.forClass(Reply.class);
        verify(replyRepository).save(captor.capture());
        assertThat(captor.getValue().getBody()).isEqualTo("Hi {{unknown}}");
    }

    @Test
    void execute_unknownActionTypeIsSkipped() {
        Ticket ticket = newTicket();

        executor.execute(ticket, "[{\"type\":\"future_action\",\"value\":\"x\"}]");

        verify(ticketRepository, never()).save(any());
        verify(replyRepository, never()).save(any());
    }

    @Test
    void execute_malformedJsonReturnsEmptyActions() {
        Ticket ticket = newTicket();

        var result = executor.execute(ticket, "not json");

        assertThat(result).isEmpty();
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void execute_emptyStringReturnsEmptyActions() {
        Ticket ticket = newTicket();

        var result = executor.execute(ticket, "");

        assertThat(result).isEmpty();
    }

    @Test
    void execute_nullReturnsEmptyActions() {
        Ticket ticket = newTicket();

        var result = executor.execute(ticket, null);

        assertThat(result).isEmpty();
    }

    @Test
    void execute_oneActionFailureDoesNotStopOthers() {
        Ticket ticket = newTicket();
        when(agentRepository.findById(anyLong()))
                .thenThrow(new RuntimeException("db offline"));

        executor.execute(ticket,
                "[{\"type\":\"assign_agent\",\"value\":\"7\"},"
                + "{\"type\":\"change_priority\",\"value\":\"urgent\"}]");

        // Despite the failure on assign_agent, change_priority still ran.
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.URGENT);
    }

    @Test
    void execute_returnsParsedActionList() {
        Ticket ticket = newTicket();

        var result = executor.execute(ticket,
                "[{\"type\":\"change_priority\",\"value\":\"high\"},"
                + "{\"type\":\"add_note\",\"value\":\"go\"}]");

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("type", "change_priority");
        assertThat(result.get(1)).containsEntry("type", "add_note");
    }

    // --- delay action ---

    @Test
    void execute_delay_pausesAndPersistsRemainingActions() {
        Ticket ticket = newTicket();
        long before = System.currentTimeMillis();

        executor.execute(ticket,
                "[{\"type\":\"change_priority\",\"value\":\"high\"},"
                + "{\"type\":\"delay\",\"value\":\"60\"},"
                + "{\"type\":\"add_note\",\"value\":\"after wait\"}]");

        // pre-delay action ran
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);
        // post-delay action did NOT run
        verify(replyRepository, never()).save(any());

        ArgumentCaptor<DeferredWorkflowJob> captor = ArgumentCaptor.forClass(DeferredWorkflowJob.class);
        verify(deferredRepository).save(captor.capture());
        DeferredWorkflowJob saved = captor.getValue();
        assertThat(saved.getTicketId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo("pending");
        assertThat(saved.getRemainingActionsJson()).contains("\"add_note\"").contains("\"after wait\"");
        assertThat(saved.getRunAt().toEpochMilli())
                .isGreaterThanOrEqualTo(before + 60_000 - 500);
    }

    @Test
    void execute_delay_invalidValueSkipsRemainingActions() {
        Ticket ticket = newTicket();

        executor.execute(ticket,
                "[{\"type\":\"delay\",\"value\":\"nonsense\"},"
                + "{\"type\":\"change_priority\",\"value\":\"urgent\"}]");

        verify(deferredRepository, never()).save(any());
        // priority should remain LOW from newTicket() — post-delay action must not have run
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.LOW);
    }

    @Test
    void runDueDeferredJobs_resumesAndMarksDone() {
        DeferredWorkflowJob job = new DeferredWorkflowJob();
        job.setId(99L);
        job.setTicketId(1L);
        job.setRemainingActionsJson("[{\"type\":\"change_priority\",\"value\":\"urgent\"}]");
        job.setRunAt(Instant.now().minusSeconds(10));
        job.setStatus("pending");

        Ticket ticket = newTicket();
        when(deferredRepository.findByStatusAndRunAtLessThanEqual(eq("pending"), any()))
                .thenReturn(List.of(job));
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        executor.runDueDeferredJobs();

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.URGENT);
        assertThat(job.getStatus()).isEqualTo("done");
        verify(deferredRepository).save(job);
    }

    @Test
    void runDueDeferredJobs_marksFailedWhenTicketMissing() {
        DeferredWorkflowJob job = new DeferredWorkflowJob();
        job.setId(99L);
        job.setTicketId(404L);
        job.setRemainingActionsJson("[]");
        job.setRunAt(Instant.now().minusSeconds(10));
        job.setStatus("pending");

        when(deferredRepository.findByStatusAndRunAtLessThanEqual(eq("pending"), any()))
                .thenReturn(List.of(job));
        when(ticketRepository.findById(404L)).thenReturn(Optional.empty());

        executor.runDueDeferredJobs();

        assertThat(job.getStatus()).isEqualTo("failed");
        assertThat(job.getLastError()).contains("404");
        verify(deferredRepository).save(job);
    }
}
