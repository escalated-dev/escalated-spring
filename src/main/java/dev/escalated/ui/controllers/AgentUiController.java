package dev.escalated.ui.controllers;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.events.CustomActionTriggeredEvent;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.services.ChatSessionService;
import dev.escalated.services.MacroService;
import dev.escalated.services.TicketActionRegistry;
import dev.escalated.services.TicketService;
import dev.escalated.services.ValidationFailedException;
import dev.escalated.ui.AgentInbox;
import dev.escalated.ui.AgentUiPages;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import dev.escalated.ui.UiTicketFilters;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * The agent pages: {@code Escalated/Agent/Dashboard}, {@code Escalated/Agent/TicketIndex} and
 * {@code Escalated/Agent/TicketShow}, and the writes TicketShow posts, as escalated-laravel's
 * {@code routes/agent.php}. Agents and admins only (the web security chain). Tickets are named by
 * reference in the path; a ticket outside the agent's department or account scope is a 404.
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/agent")
public class AgentUiController extends TicketWritesController {

    private final EscalatedUiRenderer renderer;
    private final TicketActionRegistry ticketActions;
    private final ApplicationEventPublisher events;

    @Autowired
    private ChatSessionService chats;

    @Autowired
    private AgentInbox inbox;

    @Autowired
    private dev.escalated.services.AgentPanelService panel;

    @Autowired
    private dev.escalated.ui.AdminTicketUiPages bulkActions;

    @Autowired
    private dev.escalated.security.EscalatedAuthorization authorization;

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
        super(inertia, pages, tickets, macros, agents, tags, routes);
        this.renderer = renderer;
        this.ticketActions = ticketActions;
        this.events = events;
    }

    /**
     * {@code escalated.agent.tickets.bulk}, the agent queue's bulk bar, as escalated-laravel's
     * {@code BulkActionController}: {@code ticket_ids}, {@code action} (status, priority, assign,
     * tags, department, delete, mark_read, mark_unread) and {@code value}, on the tickets the agent
     * can see. Each action needs its permission (route permissions); delete is admins only.
     */
    @PostMapping("/tickets/bulk")
    public ResponseEntity<Void> bulk(HttpServletRequest request, Authentication authentication) {
        int changed = bulkActions.bulk(UiInput.of(request), authentication.getName(),
                authorization.isAdmin(authentication));
        inertia.flash("success", changed + " ticket(s) updated.");
        return inertia.back(routes.url("escalated.agent.tickets.index"));
    }

    @GetMapping({"", "/"})
    public Object dashboard(Authentication authentication) {
        return renderer.render("Escalated/Agent/Dashboard", pages.dashboard(agentId(authentication)));
    }

    /**
     * The queue, with the agent panel's {@code inbox} and {@code queue} props ({@link AgentInbox}).
     * Besides the list filters it takes {@code assigned=me} and {@code view=<id>}; a malformed
     * {@code open}, {@code sla_at_risk}, {@code resolved_today} or {@code updated_within_hours}
     * goes back with {@code errors}.
     */
    @GetMapping("/tickets")
    public Object index(@RequestParam Map<String, String> filters, Authentication authentication,
                        HttpSession session) {
        Map<String, List<String>> errors = UiTicketFilters.validate(filters);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        Long agentId = agentId(authentication);
        Map<String, String> normalised = inbox.normalise(filters, agentId);
        AgentInbox.Queue queue = inbox.queueFor(normalised, agentId);
        inbox.remember(session, queue);

        Map<String, Object> props = pages.index(inbox.listFilters(normalised, queue), agentId);
        withPanelFields(props, agentId);
        props.put("filters", new LinkedHashMap<>(filters));
        props.put("queue", queue.prop());
        props.put("inbox", inbox.inbox(agentId, queue));
        return renderer.render("Escalated/Agent/TicketIndex", props);
    }

    @GetMapping("/tickets/{ticket}")
    public Object show(@PathVariable("ticket") String reference, Authentication authentication,
                       HttpSession session) {
        Ticket ticket = pages.resolve(reference);
        Long agentId = agentId(authentication);
        AgentInbox.Queue queue = inbox.recalled(session, agentId);
        // Opening the ticket in the agent panel is reading it.
        panel.markRead(agentId, ticket.getId());
        Map<String, Object> props = pages.show(ticket.getId(), agentId);
        if (props.get("ticket") instanceof Map<?, ?> detail) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fields = (Map<String, Object>) detail;
            fields.putAll(panel.rowFields(agentId, java.util.List.of(ticket.getId()))
                    .getOrDefault(ticket.getId(), Map.of()));
        }
        props.put("queue", queue.prop());
        props.put("inbox", inbox.inbox(agentId, queue));
        return renderer.render("Escalated/Agent/TicketShow", props);
    }

    /**
     * End Chat on TicketShow ({@code escalated.admin.chat.end}): the chat session named by id, from
     * the agent's browser session. The {@code /escalated/api/agent/chat/{id}/end} endpoint is on the
     * stateless API chain and does not see that session. Takes no body fields. 200 with a message,
     * 422 when the chat has already ended (as escalated-laravel), 404 for an unknown session.
     */
    @PostMapping("/chat/{session}/end")
    public ResponseEntity<Map<String, String>> endChat(@PathVariable("session") Long sessionId) {
        if ("ended".equals(chats.findById(sessionId).getStatus())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("message", "Chat is already ended."));
        }
        chats.end(sessionId);
        return ResponseEntity.ok(Map.of("message", "Chat ended."));
    }

    /**
     * Transfer from TicketShow's action bar ({@code escalated.admin.chat.transfer}): the chat
     * session named by id, from the agent's browser session, as the API's
     * {@code POST /escalated/api/agent/chat/{id}/transfer} does. 200, 422 or 404.
     */
    @PostMapping("/chat/{session}/transfer")
    public ResponseEntity<Map<String, Object>> transferChat(
            @PathVariable("session") Long sessionId,
            @org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, Object> body) {
        return dev.escalated.controllers.agent.AgentChatController.transfer(chats, sessionId, body);
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

    /** is_unread and the requester's presence on every row (ADR 2026-11-05-agent-panel-data). */
    @SuppressWarnings("unchecked")
    private void withPanelFields(Map<String, Object> props, Long agentId) {
        if (!(props.get("tickets") instanceof Map<?, ?> paginator) || !(paginator.get("data") instanceof List<?> rows)) {
            return;
        }
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map && map.get("id") instanceof Number id) {
                ids.add(id.longValue());
            }
        }
        Map<Long, Map<String, Object>> fields = panel.rowFields(agentId, ids);
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map && map.get("id") instanceof Number id) {
                ((Map<String, Object>) map).putAll(fields.getOrDefault(id.longValue(), Map.of()));
            }
        }
    }

    @Override
    protected String routePrefix() {
        return "escalated.agent";
    }
}
