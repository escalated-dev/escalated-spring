package dev.escalated.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.escalated.models.Macro;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.MacroRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MacroService {

    private static final Logger log = LoggerFactory.getLogger(MacroService.class);

    private final MacroRepository macroRepository;
    private final TicketService ticketService;
    private final ObjectMapper objectMapper;

    public MacroService(MacroRepository macroRepository,
                        TicketService ticketService,
                        ObjectMapper objectMapper) {
        this.macroRepository = macroRepository;
        this.ticketService = ticketService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Macro> findAll() {
        return macroRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Macro> findAccessibleByAgent(Long agentId) {
        return macroRepository.findAccessibleByAgent(agentId);
    }

    @Transactional(readOnly = true)
    public Macro findById(Long id) {
        return macroRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Macro not found: " + id));
    }

    @Transactional
    public Macro create(String name, String description, String actions, boolean shared, Long agentId) {
        Macro macro = new Macro();
        macro.setName(name);
        macro.setDescription(description);
        macro.setActions(actions);
        macro.setShared(shared);
        macro.setCreatedByAgentId(agentId);
        return macroRepository.save(macro);
    }

    @Transactional
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
                    case "set_status" -> ticketService.changeStatus(
                            ticketId, TicketStatus.valueOf(value), actorEmail);
                    case "set_priority" -> ticketService.update(
                            ticketId, null, null, TicketPriority.valueOf(value));
                    case "add_reply" -> ticketService.addReply(
                            ticketId, value, "System", actorEmail, "agent", false);
                    case "add_internal_note" -> ticketService.addReply(
                            ticketId, value, "System", actorEmail, "agent", true);
                    case "add_tag" -> ticketService.addTag(ticketId, value);
                    case "assign" -> ticketService.assign(
                            ticketId, Long.parseLong(value), actorEmail);
                    default -> log.warn("Unknown macro action type: {}", type);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to apply macro {} to ticket {}: {}", macroId, ticketId, ex.getMessage());
            throw new RuntimeException("Failed to apply macro", ex);
        }
    }

    @Transactional
    public void delete(Long id) {
        macroRepository.deleteById(id);
    }
}
