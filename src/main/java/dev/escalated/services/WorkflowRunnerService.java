package dev.escalated.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.Ticket;
import dev.escalated.models.Workflow;
import dev.escalated.models.WorkflowLog;
import dev.escalated.repositories.WorkflowLogRepository;
import dev.escalated.repositories.WorkflowRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates evaluation + execution of Workflows for a given trigger
 * event.
 *
 * <p>For each active Workflow matching the trigger (in {@code position}
 * order), evaluates conditions via {@link WorkflowEngine} and, if matched,
 * dispatches to {@link WorkflowExecutorService}. Writes a
 * {@link WorkflowLog} row per Workflow considered. Honors
 * {@code stop_on_match}.
 *
 * <p>Executor errors are caught so one misbehaving workflow never blocks
 * the rest — the failure is stamped on its log row via {@code errorMessage}.
 *
 * <p>Mirrors the NestJS reference {@code workflow-runner.service.ts}.
 */
@Service
public class WorkflowRunnerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRunnerService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkflowRepository workflowRepository;
    private final WorkflowLogRepository workflowLogRepository;
    private final WorkflowEngine engine;
    private final WorkflowExecutorService executor;

    public WorkflowRunnerService(
            WorkflowRepository workflowRepository,
            WorkflowLogRepository workflowLogRepository,
            WorkflowEngine engine,
            WorkflowExecutorService executor) {
        this.workflowRepository = workflowRepository;
        this.workflowLogRepository = workflowLogRepository;
        this.engine = engine;
        this.executor = executor;
    }

    /**
     * Load active workflows for the trigger event, evaluate conditions,
     * execute actions on the matches, and write audit logs.
     *
     * @param triggerEvent the event name, e.g. {@code "ticket.created"}
     * @param ticket       the ticket being acted on
     */
    public void runForEvent(String triggerEvent, Ticket ticket) {
        List<Workflow> workflows =
                workflowRepository.findByTriggerEventAndIsActiveTrueOrderByPositionAsc(triggerEvent);
        if (workflows.isEmpty()) {
            return;
        }

        Map<String, String> conditionMap = ticketToConditionMap(ticket);

        for (Workflow wf : workflows) {
            Instant startedAt = Instant.now();
            boolean matched = evaluate(wf, conditionMap);

            WorkflowLog logRow = new WorkflowLog();
            logRow.setWorkflow(wf);
            logRow.setTicket(ticket);
            logRow.setTriggerEvent(triggerEvent);
            logRow.setConditionsMatched(matched);
            logRow.setStartedAt(startedAt);
            logRow = workflowLogRepository.save(logRow);

            if (!matched) {
                continue;
            }

            try {
                List<Map<String, Object>> executed = executor.execute(ticket, wf.getActions());
                logRow.setActionsExecutedJson(safeSerialize(executed));
                logRow.setCompletedAt(Instant.now());
                workflowLogRepository.save(logRow);
            } catch (RuntimeException ex) {
                log.error("Workflow #{} ({}) failed on ticket #{}: {}",
                        wf.getId(), wf.getName(), ticket.getId(), ex.getMessage());
                logRow.setErrorMessage(ex.getMessage());
                logRow.setCompletedAt(Instant.now());
                workflowLogRepository.save(logRow);
            }

            if (wf.isStopOnMatch()) {
                break;
            }
        }
    }

    /**
     * Parse the workflow's {@code conditions} JSON and evaluate it
     * against the ticket field-map. A null/blank conditions payload
     * matches everything (same rule as NestJS).
     */
    private boolean evaluate(Workflow wf, Map<String, String> conditionMap) {
        String conditionsJson = wf.getConditions();
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return true;
        }
        try {
            Map<String, Object> conditions =
                    MAPPER.readValue(conditionsJson, new TypeReference<Map<String, Object>>() {});
            return engine.evaluateConditions(conditions, conditionMap);
        } catch (Exception ex) {
            log.warn("[WorkflowRunner] bad conditions JSON on workflow #{}: {}",
                    wf.getId(), ex.getMessage());
            return false;
        }
    }

    private static String safeSerialize(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Flattens a ticket into a string-keyed, string-valued map for the
     * condition evaluator (which treats all fields as strings). Relations
     * and objects are skipped.
     */
    private static Map<String, String> ticketToConditionMap(Ticket ticket) {
        Map<String, String> map = new HashMap<>();
        put(map, "id", ticket.getId());
        put(map, "subject", ticket.getSubject());
        put(map, "body", ticket.getBody());
        put(map, "ticket_number", ticket.getTicketNumber());
        put(map, "requester_name", ticket.getRequesterName());
        put(map, "requester_email", ticket.getRequesterEmail());
        if (ticket.getPriority() != null) {
            map.put("priority", ticket.getPriority().name().toLowerCase());
        }
        if (ticket.getStatus() != null) {
            map.put("status", ticket.getStatus().name().toLowerCase());
        }
        if (ticket.getDepartment() != null && ticket.getDepartment().getId() != null) {
            map.put("department_id", String.valueOf(ticket.getDepartment().getId()));
        }
        if (ticket.getAssignedAgent() != null && ticket.getAssignedAgent().getId() != null) {
            map.put("assigned_agent_id", String.valueOf(ticket.getAssignedAgent().getId()));
        }
        return map;
    }

    private static void put(Map<String, String> map, String key, Object value) {
        if (value != null) {
            map.put(key, String.valueOf(value));
        }
    }
}
