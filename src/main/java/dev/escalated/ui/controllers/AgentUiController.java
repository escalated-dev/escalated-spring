package dev.escalated.ui.controllers;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.events.CustomActionTriggeredEvent;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.services.MacroService;
import dev.escalated.services.TicketActionRegistry;
import dev.escalated.services.TicketService;
import dev.escalated.ui.AgentUiPages;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import dev.escalated.ui.UiTicketFilters;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * The agent pages: {@code Escalated/Agent/Dashboard}, {@code Escalated/Agent/TicketIndex} and
 * {@code Escalated/Agent/TicketShow}, and the writes TicketShow posts, as escalated-laravel's
 * {@code routes/agent.php}. Agents and admins only (the web security chain). Tickets are named by
 * reference in the path; a ticket outside the agent's department or account scope is a 404.
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/agent")
public class AgentUiController {

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final AgentUiPages pages;
    private final TicketService tickets;
    private final MacroService macros;
    private final TicketActionRegistry ticketActions;
    private final ApplicationEventPublisher events;
    private final AgentProfileRepository agents;
    private final TagRepository tags;
    private final UiRoutes routes;

    public AgentUiController(
            EscalatedUiRenderer renderer,
            Inertia inertia,
            AgentUiPages pages,
            TicketService tickets,
            MacroService macros,
            TicketActionRegistry ticketActions,
            ApplicationEventPublisher events,
            AgentProfileRepository agents,
            TagRepository tags,
            UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.pages = pages;
        this.tickets = tickets;
        this.macros = macros;
        this.ticketActions = ticketActions;
        this.events = events;
        this.agents = agents;
        this.tags = tags;
        this.routes = routes;
    }

    @GetMapping({"", "/"})
    public Object dashboard(Authentication authentication) {
        return renderer.render("Escalated/Agent/Dashboard", pages.dashboard(agentId(authentication)));
    }

    @GetMapping("/tickets")
    public Object index(@RequestParam Map<String, String> filters, Authentication authentication) {
        return renderer.render("Escalated/Agent/TicketIndex", pages.index(filters, agentId(authentication)));
    }

    @GetMapping("/tickets/{ticket}")
    public Object show(@PathVariable("ticket") String reference, Authentication authentication) {
        Ticket ticket = pages.resolve(reference);
        return renderer.render("Escalated/Agent/TicketShow", pages.show(ticket.getId(), agentId(authentication)));
    }

    @PostMapping("/tickets/{ticket}/reply")
    public ResponseEntity<Void> reply(@PathVariable("ticket") String reference, HttpServletRequest request,
                                      Authentication authentication) {
        return post(reference, request, authentication, false);
    }

    @PostMapping("/tickets/{ticket}/note")
    public ResponseEntity<Void> note(@PathVariable("ticket") String reference, HttpServletRequest request,
                                     Authentication authentication) {
        return post(reference, request, authentication, true);
    }

    @PostMapping("/tickets/{ticket}/assign")
    public ResponseEntity<Void> assign(@PathVariable("ticket") String reference, HttpServletRequest request,
                                       Authentication authentication) {
        Ticket ticket = pages.resolve(reference);
        long agentId = UiInput.of(request).requiredLong("agent_id");
        if (agents.findById(agentId).filter(AgentProfile::isActive).isEmpty()) {
            throw UiInput.invalid("agent_id", "The selected agent id is invalid.");
        }
        tickets.assign(ticket.getId(), agentId, authentication.getName());
        return done(ticket, "Ticket assigned.");
    }

    @PostMapping("/tickets/{ticket}/status")
    public ResponseEntity<Void> status(@PathVariable("ticket") String reference, HttpServletRequest request,
                                       Authentication authentication) {
        Ticket ticket = pages.resolve(reference);
        String given = UiInput.of(request).string("status");
        TicketStatus status = UiTicketFilters.parseStatus(given);
        if (status == null) {
            throw UiInput.invalid("status", given == null ? "The status field is required." : "The selected status is invalid.");
        }
        tickets.changeStatus(ticket.getId(), status, authentication.getName());
        return done(ticket, "Status updated.");
    }

    @PostMapping("/tickets/{ticket}/priority")
    public ResponseEntity<Void> priority(@PathVariable("ticket") String reference, HttpServletRequest request) {
        Ticket ticket = pages.resolve(reference);
        String given = UiInput.of(request).string("priority");
        TicketPriority priority = UiTicketFilters.parsePriority(given);
        if (priority == null) {
            throw UiInput.invalid("priority",
                    given == null ? "The priority field is required." : "The selected priority is invalid.");
        }
        tickets.update(ticket.getId(), null, null, priority);
        return done(ticket, "Priority updated.");
    }

    @PostMapping("/tickets/{ticket}/tags")
    public ResponseEntity<Void> tags(@PathVariable("ticket") String reference, HttpServletRequest request) {
        Ticket ticket = pages.resolve(reference);
        UiInput input = UiInput.of(request);
        if (!input.has("tag_ids")) {
            throw UiInput.invalid("tag_ids", "The tag ids field is required.");
        }
        List<Long> wanted = input.longs("tag_ids");
        List<Tag> chosen = new ArrayList<>();
        for (Long id : wanted) {
            chosen.add(tags.findById(id).orElseThrow(
                    () -> UiInput.invalid("tag_ids", "The selected tag ids is invalid.")));
        }
        List<Long> current = pages.tagIds(ticket.getId());
        for (Tag tag : chosen) {
            if (!current.contains(tag.getId())) {
                tickets.addTag(ticket.getId(), tag.getName());
            }
        }
        for (Long id : current) {
            if (!wanted.contains(id)) {
                tickets.removeTag(ticket.getId(), id);
            }
        }
        return done(ticket, "Tags updated.");
    }

    @PostMapping("/tickets/{ticket}/department")
    public ResponseEntity<Void> department(@PathVariable("ticket") String reference, HttpServletRequest request,
                                           Authentication authentication) {
        Ticket ticket = pages.resolve(reference);
        long departmentId = UiInput.of(request).requiredLong("department_id");
        tickets.changeDepartment(ticket.getId(), departmentId, authentication.getName());
        return done(ticket, "Department updated.");
    }

    @PostMapping("/tickets/{ticket}/macro")
    public ResponseEntity<Void> macro(@PathVariable("ticket") String reference, HttpServletRequest request,
                                      Authentication authentication) {
        Ticket ticket = pages.resolve(reference);
        long macroId = UiInput.of(request).requiredLong("macro_id");
        if (!pages.macroAvailable(macroId, agentId(authentication))) {
            throw new jakarta.persistence.EntityNotFoundException("Macro not found");
        }
        macros.applyMacro(macroId, ticket.getId(), authentication.getName());
        return done(ticket, "Macro applied.");
    }

    @PostMapping("/tickets/{ticket}/follow")
    public ResponseEntity<Void> follow(@PathVariable("ticket") String reference, Authentication authentication) {
        Ticket ticket = pages.resolve(reference);
        Long agentId = agentId(authentication);
        if (agentId == null) {
            return ResponseEntity.status(403).build();
        }
        boolean following = pages.toggleFollow(ticket.getId(), agentId);
        return done(ticket, following ? "You are now following this ticket." : "You are no longer following this ticket.");
    }

    @PostMapping("/tickets/{ticket}/actions/{action}")
    public ResponseEntity<Void> customAction(@PathVariable("ticket") String reference, @PathVariable String action,
                                             HttpServletRequest request, Authentication authentication) {
        Ticket ticket = pages.resolve(reference);
        EscalatedProperties.TicketActionProperties config = ticketActions.find(action);
        if (config == null || !config.isVisible()) {
            return ResponseEntity.status(404).build();
        }
        if (!config.isEnabled()) {
            return ResponseEntity.status(403).build();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (UiInput.of(request).raw("payload") instanceof Map<?, ?> given) {
            given.forEach((key, value) -> payload.put(String.valueOf(key), value));
        }
        tickets.addReply(ticket.getId(), "Custom action \"" + action + "\" was triggered.",
                "System", authentication.getName(), "agent", true);
        events.publishEvent(new CustomActionTriggeredEvent(this, tickets.findById(ticket.getId()), action,
                authentication.getName(), payload, config.getMetadata()));
        return done(ticket, "Custom action dispatched.");
    }

    private ResponseEntity<Void> post(String reference, HttpServletRequest request, Authentication authentication,
                                      boolean internal) {
        Ticket ticket = pages.resolve(reference);
        refuseFiles(request);
        String body = UiInput.of(request).requiredString("body", 65535);
        AgentProfile agent = agents.findByEmail(authentication.getName()).orElse(null);
        String name = agent != null && agent.getName() != null && !agent.getName().isBlank()
                ? agent.getName() : authentication.getName();
        tickets.addReply(ticket.getId(), body, name, authentication.getName(), "agent", internal);
        return done(ticket, internal ? "Note added." : "Reply sent.");
    }

    /** Attachments are not stored from the pages yet; refusing them beats dropping them unseen. */
    private static void refuseFiles(HttpServletRequest request) {
        if (request instanceof MultipartHttpServletRequest multipart && !multipart.getMultiFileMap().isEmpty()) {
            throw UiInput.invalid("attachments", "Attachments cannot be uploaded here yet.");
        }
    }

    private ResponseEntity<Void> done(Ticket ticket, String message) {
        inertia.flash("success", message);
        return inertia.back(routes.url("escalated.agent.tickets.show", ticket.getTicketNumber()));
    }

    private Long agentId(Authentication authentication) {
        return agents.findByEmail(authentication.getName()).map(AgentProfile::getId).orElse(null);
    }
}
