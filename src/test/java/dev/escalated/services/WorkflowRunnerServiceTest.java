package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.models.Workflow;
import dev.escalated.models.WorkflowLog;
import dev.escalated.repositories.WorkflowLogRepository;
import dev.escalated.repositories.WorkflowRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link WorkflowRunnerService}.
 *
 * <p>Uses a real {@link WorkflowEngine} + spy'd {@link WorkflowExecutorService}
 * so the condition-evaluation / log-writing orchestration path is exercised
 * end-to-end. Mirrors the NestJS reference {@code workflow-runner.service.ts}.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowRunnerServiceTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowLogRepository workflowLogRepository;
    @Mock private WorkflowExecutorService executor;

    private WorkflowRunnerService runner;

    @BeforeEach
    void setUp() {
        runner = new WorkflowRunnerService(
                workflowRepository, workflowLogRepository, new WorkflowEngine(), executor);
        when(workflowLogRepository.save(any(WorkflowLog.class)))
                .thenAnswer(inv -> {
                    WorkflowLog l = inv.getArgument(0);
                    if (l.getId() == null) {
                        l.setId(1L);
                    }
                    return l;
                });
    }

    private Ticket newTicket() {
        Ticket t = new Ticket();
        t.setId(1L);
        t.setSubject("Help");
        t.setPriority(TicketPriority.MEDIUM);
        t.setStatus(TicketStatus.OPEN);
        return t;
    }

    private Workflow workflow(long id, String name, String conditions, boolean stopOnMatch) {
        Workflow wf = new Workflow();
        wf.setId(id);
        wf.setName(name);
        wf.setTriggerEvent("ticket.created");
        wf.setConditions(conditions);
        wf.setActions("[{\"type\":\"add_note\",\"value\":\"hi\"}]");
        wf.setActive(true);
        wf.setStopOnMatch(stopOnMatch);
        return wf;
    }

    @Test
    void runForEvent_noMatchingWorkflows_doesNothing() {
        when(workflowRepository.findByTriggerEventAndIsActiveTrueOrderByPositionAsc("ticket.created"))
                .thenReturn(List.of());

        runner.runForEvent("ticket.created", newTicket());

        verify(executor, never()).execute(any(), anyString());
        verify(workflowLogRepository, never()).save(any(WorkflowLog.class));
    }

    @Test
    void runForEvent_matched_executesAndLogs() {
        Workflow wf = workflow(1L, "A", null, false); // null conditions = match all
        when(workflowRepository.findByTriggerEventAndIsActiveTrueOrderByPositionAsc(anyString()))
                .thenReturn(List.of(wf));
        when(executor.execute(any(Ticket.class), anyString()))
                .thenReturn(List.of(Map.of("type", "add_note", "value", "hi")));

        runner.runForEvent("ticket.created", newTicket());

        verify(executor, times(1)).execute(any(Ticket.class), anyString());
        ArgumentCaptor<WorkflowLog> captor = ArgumentCaptor.forClass(WorkflowLog.class);
        // One save for the initial row + one update after execution.
        verify(workflowLogRepository, times(2)).save(captor.capture());
        WorkflowLog finalLog = captor.getAllValues().get(1);
        assertThat(finalLog.isConditionsMatched()).isTrue();
        assertThat(finalLog.getCompletedAt()).isNotNull();
        assertThat(finalLog.getErrorMessage()).isNull();
        assertThat(finalLog.getActionsExecutedJson()).contains("add_note");
    }

    @Test
    void runForEvent_unmatched_logsButDoesNotExecute() {
        // Conditions require status=closed, ticket is open.
        String conditions = "{\"all\":[{\"field\":\"status\",\"operator\":\"equals\",\"value\":\"closed\"}]}";
        Workflow wf = workflow(1L, "A", conditions, false);
        when(workflowRepository.findByTriggerEventAndIsActiveTrueOrderByPositionAsc(anyString()))
                .thenReturn(List.of(wf));

        runner.runForEvent("ticket.created", newTicket());

        verify(executor, never()).execute(any(), anyString());
        verify(workflowLogRepository, times(1)).save(any(WorkflowLog.class));
    }

    @Test
    void runForEvent_malformedConditionsJson_doesNotMatch() {
        Workflow wf = workflow(1L, "A", "{ not: valid json", false);
        when(workflowRepository.findByTriggerEventAndIsActiveTrueOrderByPositionAsc(anyString()))
                .thenReturn(List.of(wf));

        runner.runForEvent("ticket.created", newTicket());

        verify(executor, never()).execute(any(), anyString());
        ArgumentCaptor<WorkflowLog> captor = ArgumentCaptor.forClass(WorkflowLog.class);
        verify(workflowLogRepository).save(captor.capture());
        assertThat(captor.getValue().isConditionsMatched()).isFalse();
    }

    @Test
    void runForEvent_stopOnMatch_haltsAfterFirstMatch() {
        Workflow first = workflow(1L, "first", null, true);   // stopOnMatch = true
        Workflow second = workflow(2L, "second", null, false);
        when(workflowRepository.findByTriggerEventAndIsActiveTrueOrderByPositionAsc(anyString()))
                .thenReturn(List.of(first, second));
        when(executor.execute(any(Ticket.class), anyString()))
                .thenReturn(List.of());

        runner.runForEvent("ticket.created", newTicket());

        // Only the first workflow should have been executed.
        verify(executor, times(1)).execute(any(), anyString());
    }

    @Test
    void runForEvent_stopOnMatch_onlyAppliesOnMatch() {
        // first is stop_on_match=true but doesn't match (status=closed condition).
        // Second should still run.
        String failingCond = "{\"all\":[{\"field\":\"status\",\"operator\":\"equals\",\"value\":\"closed\"}]}";
        Workflow first = workflow(1L, "first", failingCond, true);
        Workflow second = workflow(2L, "second", null, false);
        when(workflowRepository.findByTriggerEventAndIsActiveTrueOrderByPositionAsc(anyString()))
                .thenReturn(List.of(first, second));
        when(executor.execute(any(Ticket.class), anyString()))
                .thenReturn(List.of());

        runner.runForEvent("ticket.created", newTicket());

        // second workflow matched (null conditions) and ran.
        verify(executor, times(1)).execute(any(), anyString());
    }

    @Test
    void runForEvent_executorFailureIsCaughtAndStamped() {
        Workflow wf = workflow(1L, "A", null, false);
        when(workflowRepository.findByTriggerEventAndIsActiveTrueOrderByPositionAsc(anyString()))
                .thenReturn(List.of(wf));
        when(executor.execute(any(Ticket.class), anyString()))
                .thenThrow(new RuntimeException("db offline"));

        // Should not throw — failure is stamped on the log row.
        runner.runForEvent("ticket.created", newTicket());

        ArgumentCaptor<WorkflowLog> captor = ArgumentCaptor.forClass(WorkflowLog.class);
        verify(workflowLogRepository, times(2)).save(captor.capture());
        WorkflowLog finalLog = captor.getAllValues().get(1);
        assertThat(finalLog.getErrorMessage()).isEqualTo("db offline");
        assertThat(finalLog.getCompletedAt()).isNotNull();
    }

    @Test
    void runForEvent_multipleWorkflows_failureDoesNotBlockLater() {
        Workflow first = workflow(1L, "first", null, false);
        Workflow second = workflow(2L, "second", null, false);
        when(workflowRepository.findByTriggerEventAndIsActiveTrueOrderByPositionAsc(anyString()))
                .thenReturn(List.of(first, second));
        when(executor.execute(any(Ticket.class), anyString()))
                .thenThrow(new RuntimeException("first failed"))
                .thenReturn(List.of());

        runner.runForEvent("ticket.created", newTicket());

        // Both workflows should have been tried despite the first one failing.
        verify(executor, times(2)).execute(any(), anyString());
    }
}
