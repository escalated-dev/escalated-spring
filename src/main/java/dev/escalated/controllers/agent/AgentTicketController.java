package dev.escalated.controllers.agent;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.dto.TicketDetailDto;
import dev.escalated.events.CustomActionTriggeredEvent;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.MacroService;
import dev.escalated.services.SideConversationService;
import dev.escalated.services.TicketActionRegistry;
import dev.escalated.services.TicketCustomFieldValues;
import dev.escalated.services.TicketLinkService;
import dev.escalated.services.TicketService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/escalated/api/agent/tickets")
public class AgentTicketController {

    private final TicketService ticketService;
    private final MacroService macroService;
    private final SideConversationService sideConversationService;
    private final TicketLinkService ticketLinkService;
    private final TicketActionRegistry ticketActionRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final AgentProfileRepository agents;
    private final TicketCustomFieldValues customFields;

    public AgentTicketController(TicketService ticketService,
                                 MacroService macroService,
                                 SideConversationService sideConversationService,
                                 TicketLinkService ticketLinkService,
                                 TicketActionRegistry ticketActionRegistry,
                                 ApplicationEventPublisher eventPublisher,
                                 AgentProfileRepository agents,
                                 TicketCustomFieldValues customFields) {
        this.agents = agents;
        this.customFields = customFields;
        this.ticketService = ticketService;
        this.macroService = macroService;
        this.sideConversationService = sideConversationService;
        this.ticketLinkService = ticketLinkService;
        this.ticketActionRegistry = ticketActionRegistry;
        this.eventPublisher = eventPublisher;
    }

    private List<Map<String, Object>> customActionsForTicket(Long ticketId) {
        List<Map<String, Object>> actions = ticketActionRegistry.forTicket();
        for (Map<String, Object> action : actions) {
            action.put(
                    "url",
                    "/escalated/api/agent/tickets/" + ticketId + "/actions/" + action.get("key"));
            action.put("method", "post");
        }
        return actions;
    }

    @GetMapping
    public ResponseEntity<Page<Ticket>> index(
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(name = "department_id", required = false) Long departmentId,
            @RequestParam(name = "ticket_type", required = false) String ticketType,
            @RequestParam(name = "account_type", required = false) String accountType,
            @RequestParam(name = "account_id", required = false) String accountId,
            @PageableDefault(size = 25) Pageable pageable) {
        if (query != null
                || departmentId != null
                || ticketType != null
                || accountType != null
                || accountId != null) {
            return ResponseEntity.ok(
                    ticketService.search(
                            query,
                            agentId,
                            status,
                            departmentId,
                            ticketType,
                            accountType,
                            accountId,
                            pageable));
        }
        if (agentId != null) {
            return ResponseEntity.ok(ticketService.findByAssignedAgent(agentId, pageable));
        }
        if (status != null) {
            return ResponseEntity.ok(ticketService.findByStatus(status, pageable));
        }
        return ResponseEntity.ok(ticketService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDetailDto> show(@PathVariable Long id) {
        TicketDetailDto dto = ticketService.findByIdWithDetail(id);
        dto.setCustomActions(customActionsForTicket(id));
        dto.setCustomFields(customFields.forTicket(id));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/actions/{action}")
    public ResponseEntity<Object> customAction(@PathVariable Long id, @PathVariable String action,
                                               @RequestBody(required = false) Map<String, Object> body,
                                               Authentication authentication) {
        EscalatedProperties.TicketActionProperties config = ticketActionRegistry.find(action);
        if (config == null || !config.isVisible()) {
            return ResponseEntity.status(404).body(Map.of("error", "Custom action not found"));
        }
        if (!config.isEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "Custom action is not enabled"));
        }

        Ticket ticket = ticketService.findById(id);
        String actorEmail = authentication.getName();

        Map<String, Object> payload = new LinkedHashMap<>();
        Object rawPayload = body == null ? null : body.get("payload");
        if (rawPayload instanceof Map<?, ?> map) {
            map.forEach((k, v) -> payload.put(String.valueOf(k), v));
        }

        // Record an internal note for auditability.
        ticketService.addReply(id, "Custom action \"" + action + "\" was triggered.",
                "System", actorEmail, "agent", true);

        eventPublisher.publishEvent(new CustomActionTriggeredEvent(this, ticket, action, actorEmail,
                payload, config.getMetadata()));

        return ResponseEntity.ok(Map.of("message", "Custom action dispatched.", "action", action));
    }

    @GetMapping("/{id}/replies")
    public ResponseEntity<List<Reply>> replies(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getReplies(id));
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<Reply> addReply(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                          Authentication authentication) {
        // The author is the signed-in agent. author_name, author_email and
        // author_type in the body are ignored: an agent cannot post as the
        // requester or as a colleague.
        AgentProfile agent = agents.findByEmail(authentication.getName()).orElse(null);
        return ResponseEntity.status(201).body(ticketService.addReply(id,
                (String) body.get("body"),
                authorName(agent, authentication),
                authentication.getName(),
                "agent",
                Boolean.parseBoolean(body.getOrDefault("internal", "false").toString())));
    }

    private static String authorName(AgentProfile agent, Authentication authentication) {
        return agent != null && agent.getName() != null && !agent.getName().isBlank()
                ? agent.getName()
                : authentication.getName();
    }

    @PostMapping("/{id}/macro/{macroId}")
    public ResponseEntity<Void> applyMacro(
            @PathVariable Long id,
            @PathVariable Long macroId,
            Authentication authentication) {
        macroService.applyMacro(macroId, id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/side-conversations")
    public ResponseEntity<Object> createSideConversation(@PathVariable Long id,
                                                         @RequestBody Map<String, String> body,
                                                         Authentication authentication) {
        AgentProfile agent = agents.findByEmail(authentication.getName()).orElse(null);
        return ResponseEntity.status(201).body(sideConversationService.create(id,
                body.get("subject"),
                body.get("participant_emails"),
                body.get("message"),
                authorName(agent, authentication),
                authentication.getName()));
    }

    @PostMapping("/{id}/links")
    public ResponseEntity<Object> createLink(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(ticketLinkService.create(id,
                Long.valueOf(body.get("target_ticket_id").toString()),
                (String) body.getOrDefault("link_type", "related")));
    }
}
