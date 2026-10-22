package dev.escalated.ui.controllers;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.services.MacroService;
import dev.escalated.services.TicketService;
import dev.escalated.ui.AgentUiPages;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import dev.escalated.ui.UiTicketFilters;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * The writes a ticket page posts, shared by the agent ({@code /escalated/agent/tickets/...}) and
 * admin ({@code /escalated/admin/tickets/...}) pages, as escalated-laravel's agent and admin
 * {@code TicketController}s: {@code reply}, {@code note}, {@code assign}, {@code status},
 * {@code priority}, {@code tags}, {@code department}, {@code macro} and {@code follow}. Each goes
 * back to the ticket with a flash; tickets are named by reference.
 */
public abstract class TicketWritesController {

    protected final Inertia inertia;
    protected final AgentUiPages pages;
    protected final TicketService tickets;
    protected final MacroService macros;
    protected final AgentProfileRepository agents;
    protected final TagRepository tags;
    protected final UiRoutes routes;

    @org.springframework.beans.factory.annotation.Autowired
    protected dev.escalated.services.TicketStatuses statuses;

    protected TicketWritesController(Inertia inertia, AgentUiPages pages, TicketService tickets, MacroService macros,
                                     AgentProfileRepository agents, TagRepository tags, UiRoutes routes) {
        this.inertia = inertia;
        this.pages = pages;
        this.tickets = tickets;
        this.macros = macros;
        this.agents = agents;
        this.tags = tags;
        this.routes = routes;
    }

    /** The route name prefix the pages use: {@code escalated.agent} or {@code escalated.admin}. */
    protected abstract String routePrefix();

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
        // A built-in status, or a custom status slug.
        if (given == null || !statuses.isKnown(given)) {
            throw UiInput.invalid("status", given == null ? "The status field is required." : "The selected status is invalid.");
        }
        tickets.changeStatusByName(ticket.getId(), given, authentication.getName());
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
    protected static void refuseFiles(HttpServletRequest request) {
        if (request instanceof MultipartHttpServletRequest multipart && !multipart.getMultiFileMap().isEmpty()) {
            throw UiInput.invalid("attachments", "Attachments cannot be uploaded here yet.");
        }
    }

    protected ResponseEntity<Void> done(Ticket ticket, String message) {
        inertia.flash("success", message);
        return inertia.back(routes.url(routePrefix() + ".tickets.show", ticket.getTicketNumber()));
    }

    protected Long agentId(Authentication authentication) {
        return agents.findByEmail(authentication.getName()).map(AgentProfile::getId).orElse(null);
    }
}
