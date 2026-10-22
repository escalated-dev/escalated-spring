package dev.escalated.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.models.Macro;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.MacroRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MacroService {

    private static final Logger log = LoggerFactory.getLogger(MacroService.class);

    private final MacroRepository macroRepository;
    private final TicketService ticketService;
    private final ObjectMapper objectMapper;
    private final AgentProfileRepository agentRepository;
    private final DepartmentRepository departmentRepository;

    public MacroService(MacroRepository macroRepository,
                        TicketService ticketService,
                        ObjectMapper objectMapper) {
        this(macroRepository, ticketService, objectMapper, null, null);
    }

    @Autowired
    public MacroService(MacroRepository macroRepository,
                        TicketService ticketService,
                        ObjectMapper objectMapper,
                        AgentProfileRepository agentRepository,
                        DepartmentRepository departmentRepository) {
        this.macroRepository = macroRepository;
        this.ticketService = ticketService;
        this.objectMapper = objectMapper;
        this.agentRepository = agentRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Macro> findAll() {
        return macroRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public List<Macro> findAccessibleByAgent(Long agentId) {
        return macroRepository.findAccessibleByAgent(agentId);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Macro findById(Long id) {
        return macroRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Macro not found: " + id));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Macro create(String name, String description, String actions, boolean shared, Long agentId) {
        Macro macro = new Macro();
        macro.setName(name);
        macro.setDescription(description);
        macro.setActions(actions);
        macro.setShared(shared);
        macro.setCreatedByAgentId(agentId);
        return macroRepository.save(macro);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void applyMacro(Long macroId, Long ticketId, String actorEmail) {
        Macro macro = findById(macroId);
        Ticket ticket = ticketService.findById(ticketId);

        try {
            List<Map<String, String>> actions = objectMapper.readValue(
                    macro.getActions(), new TypeReference<>() {});

            for (Map<String, String> action : actions) {
                String type = action.get("type");
                String value = action.get("value");

                switch (type) {
                    case "set_status" -> setStatus(ticketId, value, actorEmail);
                    case "set_priority" -> ticketService.update(
                            ticketId, null, null, TicketPriority.valueOf(value));
                    case "add_reply" -> ticketService.addReply(
                            ticketId, value, "System", actorEmail, "agent", false);
                    case "add_internal_note" -> ticketService.addReply(
                            ticketId, value, "System", actorEmail, "agent", true);
                    case "add_tag" -> ticketService.addTag(ticketId, value);
                    case "assign" -> ticketService.assign(ticketId, agentId(value), actorEmail);
                    // The shared admin page's names, as escalated-laravel's MacroService.
                    case "status" -> setStatus(ticketId, value, actorEmail);
                    case "priority" -> ticketService.update(
                            ticketId, null, null, TicketPriority.valueOf(value.trim().toUpperCase(Locale.ROOT)));
                    case "tags" -> {
                        for (String tag : value.split(",")) {
                            if (!tag.isBlank()) {
                                ticketService.addTag(ticketId, tag.trim());
                            }
                        }
                    }
                    case "department" -> ticketService.changeDepartment(ticketId, departmentId(value), actorEmail);
                    case "reply" -> ticketService.addReply(ticketId, value, "System", actorEmail, "agent", false);
                    case "note" -> ticketService.addReply(ticketId, value, "System", actorEmail, "agent", true);
                    default -> log.warn("Unknown macro action type: {}", type);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to apply macro {} to ticket {}: {}", macroId, ticketId, ex.getMessage());
            throw new RuntimeException("Failed to apply macro", ex);
        }
    }

    /** The macro action types the shared admin page offers, as escalated-laravel's. */
    public static final List<String> PAGE_ACTION_TYPES =
            List.of("status", "priority", "assign", "tags", "department", "reply", "note");

    /** A built-in status (any case, or the frontend's waiting states) or a custom status slug. */
    private void setStatus(Long ticketId, String value, String actorEmail) {
        dev.escalated.models.TicketStatus builtIn = TicketStatuses.builtIn(value);
        if (builtIn != null) {
            ticketService.changeStatus(ticketId, builtIn, actorEmail);
        } else {
            ticketService.changeStatusByName(ticketId, value, actorEmail);
        }
    }

    /** An agent by id or, as the admin page allows, by email. */
    private Long agentId(String value) {
        String given = value.trim();
        if (given.matches("[0-9]+")) {
            return Long.parseLong(given);
        }
        if (agentRepository == null) {
            throw new IllegalArgumentException("Unknown agent: " + given);
        }
        return agentRepository.findByEmail(given).map(AgentProfile::getId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent: " + given));
    }

    /** A department by id or, as the admin page allows, by name. */
    private Long departmentId(String value) {
        String given = value.trim();
        if (given.matches("[0-9]+")) {
            return Long.parseLong(given);
        }
        if (departmentRepository == null) {
            throw new IllegalArgumentException("Unknown department: " + given);
        }
        return departmentRepository.findByName(given).map(Department::getId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown department: " + given));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void delete(Long id) {
        macroRepository.deleteById(id);
    }
}
