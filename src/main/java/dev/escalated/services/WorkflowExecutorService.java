package dev.escalated.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.models.Reply;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Performs the side-effects dictated by a matched {@code Workflow}.
 *
 * <p>Distinct from {@link WorkflowEngine}, which only evaluates
 * conditions. This service parses the JSON action array stored on
 * {@code Workflow.actions} and dispatches each entry against the
 * relevant repository.
 *
 * <p>Action catalog: {@code change_priority}, {@code change_status},
 * {@code assign_agent}, {@code set_department}, {@code add_tag},
 * {@code remove_tag}, {@code add_note}, {@code insert_canned_reply}.
 * Mirrors the NestJS reference impl in
 * {@code escalated-nestjs/src/services/workflow-executor.service.ts}.
 *
 * <p>Unknown or malformed actions are logged at {@code warn} and
 * skipped — one bad action never halts execution of the other
 * actions on the same workflow.
 */
@Service
public class WorkflowExecutorService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutorService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TicketRepository ticketRepository;
    private final TagRepository tagRepository;
    private final AgentProfileRepository agentRepository;
    private final DepartmentRepository departmentRepository;
    private final ReplyRepository replyRepository;

    public WorkflowExecutorService(
            TicketRepository ticketRepository,
            TagRepository tagRepository,
            AgentProfileRepository agentRepository,
            DepartmentRepository departmentRepository,
            ReplyRepository replyRepository) {
        this.ticketRepository = ticketRepository;
        this.tagRepository = tagRepository;
        this.agentRepository = agentRepository;
        this.departmentRepository = departmentRepository;
        this.replyRepository = replyRepository;
    }

    /**
     * Execute every action in {@code actionsJson} against {@code ticket}.
     * Returns the list of parsed action maps so callers (e.g. the
     * runner) can serialize them into a {@code WorkflowLog} audit row.
     *
     * @param actionsJson the JSON string stored on {@code Workflow.actions}
     * @return parsed actions (never null; empty on malformed input)
     */
    public List<Map<String, Object>> execute(Ticket ticket, String actionsJson) {
        List<Map<String, Object>> actions = parseActions(actionsJson);
        for (Map<String, Object> action : actions) {
            try {
                dispatch(ticket, action);
            } catch (RuntimeException ex) {
                log.warn("[WorkflowExecutor] action {} failed on ticket #{}: {}",
                        action.get("type"), ticket.getId(), ex.getMessage());
            }
        }
        return actions;
    }

    private List<Map<String, Object>> parseActions(String actionsJson) {
        if (actionsJson == null || actionsJson.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(actionsJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            log.warn("[WorkflowExecutor] failed to parse actions JSON: {}", ex.getMessage());
            return List.of();
        }
    }

    private void dispatch(Ticket ticket, Map<String, Object> action) {
        String type = String.valueOf(action.getOrDefault("type", ""));
        String value = action.get("value") == null ? "" : String.valueOf(action.get("value"));
        switch (type) {
            case "change_priority" -> changePriority(ticket, value);
            case "change_status" -> changeStatus(ticket, value);
            case "assign_agent" -> assignAgent(ticket, value);
            case "set_department" -> setDepartment(ticket, value);
            case "add_tag" -> addTag(ticket, value);
            case "remove_tag" -> removeTag(ticket, value);
            case "add_note" -> addNote(ticket, value);
            case "insert_canned_reply" -> insertCannedReply(ticket, value);
            default -> log.warn("[WorkflowExecutor] unknown action type: {}", type);
        }
    }

    private void changePriority(Ticket ticket, String value) {
        try {
            ticket.setPriority(TicketPriority.valueOf(value.toUpperCase()));
            ticketRepository.save(ticket);
        } catch (IllegalArgumentException ex) {
            log.warn("[WorkflowExecutor] change_priority: invalid priority '{}'", value);
        }
    }

    private void changeStatus(Ticket ticket, String value) {
        try {
            ticket.setStatus(TicketStatus.valueOf(value.toUpperCase()));
            ticketRepository.save(ticket);
        } catch (IllegalArgumentException ex) {
            log.warn("[WorkflowExecutor] change_status: invalid status '{}'", value);
        }
    }

    private void assignAgent(Ticket ticket, String value) {
        Long agentId = parseLong(value);
        if (agentId == null) {
            return;
        }
        Optional<AgentProfile> agent = agentRepository.findById(agentId);
        if (agent.isEmpty()) {
            log.warn("[WorkflowExecutor] assign_agent: agent #{} not found", agentId);
            return;
        }
        ticket.setAssignedAgent(agent.get());
        ticketRepository.save(ticket);
    }

    private void setDepartment(Ticket ticket, String value) {
        Long deptId = parseLong(value);
        if (deptId == null) {
            return;
        }
        Optional<Department> dept = departmentRepository.findById(deptId);
        if (dept.isEmpty()) {
            log.warn("[WorkflowExecutor] set_department: department #{} not found", deptId);
            return;
        }
        ticket.setDepartment(dept.get());
        ticketRepository.save(ticket);
    }

    private void addTag(Ticket ticket, String value) {
        Tag tag = resolveTag(value);
        if (tag == null) {
            log.warn("[WorkflowExecutor] add_tag: tag '{}' not found", value);
            return;
        }
        ticket.getTags().add(tag);
        ticketRepository.save(ticket);
    }

    private void removeTag(Ticket ticket, String value) {
        Tag tag = resolveTag(value);
        if (tag == null) {
            return;
        }
        ticket.getTags().removeIf(t -> t.getId().equals(tag.getId()));
        ticketRepository.save(ticket);
    }

    private Tag resolveTag(String value) {
        Optional<Tag> byName = tagRepository.findByName(value);
        if (byName.isPresent()) {
            return byName.get();
        }
        Long asId = parseLong(value);
        if (asId != null) {
            return tagRepository.findById(asId).orElse(null);
        }
        return null;
    }

    private void addNote(Ticket ticket, String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        Reply note = new Reply();
        note.setTicket(ticket);
        note.setBody(body);
        note.setAuthorType("system");
        note.setInternal(true);
        replyRepository.save(note);
    }

    /**
     * Insert an agent-visible reply built from a template. {@code {{field}}}
     * placeholders are interpolated against the ticket via
     * {@link WorkflowEngine#interpolateVariables}. Unknown variables stay
     * as literal {@code {{...}}} so the reader can see the gap.
     */
    private void insertCannedReply(Ticket ticket, String template) {
        if (template == null || template.isBlank()) {
            return;
        }
        Map<String, String> ticketMap = ticketToMap(ticket);
        String body = WorkflowEngine.interpolateVariables(template, ticketMap);
        Reply reply = new Reply();
        reply.setTicket(ticket);
        reply.setBody(body);
        reply.setAuthorType("system");
        reply.setInternal(false);
        replyRepository.save(reply);
    }

    private static Map<String, String> ticketToMap(Ticket ticket) {
        Map<String, String> map = new HashMap<>();
        if (ticket.getSubject() != null) {
            map.put("subject", ticket.getSubject());
        }
        if (ticket.getBody() != null) {
            map.put("body", ticket.getBody());
        }
        if (ticket.getTicketNumber() != null) {
            map.put("ticket_number", ticket.getTicketNumber());
        }
        if (ticket.getRequesterName() != null) {
            map.put("requester_name", ticket.getRequesterName());
        }
        if (ticket.getRequesterEmail() != null) {
            map.put("requester_email", ticket.getRequesterEmail());
        }
        if (ticket.getPriority() != null) {
            map.put("priority", ticket.getPriority().name().toLowerCase());
        }
        if (ticket.getStatus() != null) {
            map.put("status", ticket.getStatus().name().toLowerCase());
        }
        return map;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
