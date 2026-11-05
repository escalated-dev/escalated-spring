package dev.escalated.ui.controllers;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.KnowledgeBaseArticle;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.AgentPanelService;
import dev.escalated.ui.AgentUiPages;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The agent panel's own endpoints (escalated-developer-context ADR 2026-11-05-agent-panel-data):
 * the agent's status, knowledge-base search for the reply composer, the agent's read markers and
 * their in-app notifications. Agents and admins only (the web security chain).
 */
@RestController
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/agent")
public class AgentPanelUiController {

    private static final int KB_DEFAULT_LIMIT = 10;
    private static final int KB_MAX_LIMIT = 20;
    private static final int EXCERPT_LENGTH = 160;

    private final AgentPanelService panel;
    private final AgentProfileRepository agents;
    private final AgentUiPages pages;
    private final Inertia inertia;
    private final EscalatedProperties properties;

    public AgentPanelUiController(AgentPanelService panel, AgentProfileRepository agents, AgentUiPages pages,
                                  Inertia inertia, EscalatedProperties properties) {
        this.panel = panel;
        this.agents = agents;
        this.pages = pages;
        this.inertia = inertia;
        this.properties = properties;
    }

    private Long agentId(Authentication authentication) {
        return agents.findByEmail(authentication.getName()).map(AgentProfile::getId).orElse(null);
    }

    /**
     * {@code escalated.agent.status}: sets the agent's status (online, away or offline).
     * {@code {agent_status}}, or back for an Inertia visit; anything else is a 422.
     */
    @RequestMapping(value = "/status", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<?> status(HttpServletRequest request, Authentication authentication) {
        String status = UiInput.of(request).string("status");
        Long agentId = agentId(authentication);
        if (agentId == null) {
            return ResponseEntity.status(403).build();
        }
        if (status == null || !AgentPanelService.STATUSES.contains(status)) {
            Map<String, List<String>> errors = Map.of("status", List.of("The selected status is invalid."));
            if (Inertia.isInertia(request)) {
                inertia.errors(errors);
                return inertia.back(UiRoutes.PREFIX + "/agent/tickets");
            }
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("message", "The selected status is invalid.", "errors", errors));
        }
        panel.setStatus(agentId, status);
        if (Inertia.isInertia(request)) {
            return inertia.back(UiRoutes.PREFIX + "/agent/tickets");
        }
        return ResponseEntity.ok(Map.of("message", "Status updated.", "agent_status", status));
    }

    /**
     * {@code escalated.agent.kb.search}: published articles whose title or body contains {@code q}
     * (2 to 200 characters), title matches first; {@code limit} 1 to 20, default 10. 404 while the
     * knowledge base is off.
     */
    @GetMapping("/kb/search")
    public ResponseEntity<?> kbSearch(@RequestParam(value = "q", required = false) String q,
                                      @RequestParam(value = "limit", required = false) String limitParam) {
        if (!properties.getKnowledgeBase().isEnabled()) {
            return ResponseEntity.status(404).body(Map.of("message", "Knowledge base is not enabled."));
        }
        String term = q == null ? "" : q.trim();
        Map<String, List<String>> errors = new LinkedHashMap<>();
        if (term.codePointCount(0, term.length()) < 2) {
            errors.put("q", List.of("The q field must be at least 2 characters."));
        } else if (term.codePointCount(0, term.length()) > 200) {
            errors.put("q", List.of("The q field must not be greater than 200 characters."));
        }
        int limit = KB_DEFAULT_LIMIT;
        if (limitParam != null && !limitParam.isBlank()) {
            try {
                limit = Integer.parseInt(limitParam.trim());
            } catch (NumberFormatException notANumber) {
                limit = 0;
            }
            if (limit < 1 || limit > KB_MAX_LIMIT) {
                errors.put("limit", List.of("The limit field must be between 1 and 20."));
            }
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "message", errors.values().iterator().next().get(0), "errors", errors));
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (KnowledgeBaseArticle article : panel.searchArticles(term, limit)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", article.getId());
            item.put("title", article.getTitle());
            item.put("excerpt", excerpt(article.getContent()));
            item.put("url", ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path(UiRoutes.PREFIX + "/kb/" + article.getSlug()).toUriString());
            if (article.getCategory() == null) {
                item.put("category", null);
            } else {
                Map<String, Object> category = new LinkedHashMap<>();
                category.put("id", article.getCategory().getId());
                category.put("name", article.getCategory().getName());
                item.put("category", category);
            }
            item.put("updated_at", article.getUpdatedAt() == null ? null : article.getUpdatedAt().toString());
            data.add(item);
        }
        return ResponseEntity.ok(Map.of("data", data));
    }

    /** The article body as plain text, cut at about 160 characters. */
    static String excerpt(String body) {
        String text = (body == null ? "" : body).replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (text.codePointCount(0, text.length()) <= EXCERPT_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, EXCERPT_LENGTH)).trim() + "...";
    }

    /** {@code escalated.agent.tickets.read}: {@code {is_unread: false}}; an Inertia visit goes back. */
    @PostMapping("/tickets/{ticket}/read")
    public ResponseEntity<?> read(@PathVariable("ticket") String reference, HttpServletRequest request,
                                  Authentication authentication) {
        return mark(reference, request, authentication, false);
    }

    /** {@code escalated.agent.tickets.unread}: {@code {is_unread: true}}; an Inertia visit goes back. */
    @PostMapping("/tickets/{ticket}/unread")
    public ResponseEntity<?> unread(@PathVariable("ticket") String reference, HttpServletRequest request,
                                    Authentication authentication) {
        return mark(reference, request, authentication, true);
    }

    private ResponseEntity<?> mark(String reference, HttpServletRequest request, Authentication authentication,
                                   boolean unread) {
        Ticket ticket = pages.resolve(reference);
        Long agentId = agentId(authentication);
        if (agentId == null) {
            return ResponseEntity.status(403).build();
        }
        if (unread) {
            panel.markUnread(agentId, ticket.getId());
        } else {
            panel.markRead(agentId, ticket.getId());
        }
        if (Inertia.isInertia(request)) {
            return inertia.back(UiRoutes.PREFIX + "/agent/tickets/" + ticket.getTicketNumber());
        }
        return ResponseEntity.ok(Map.of("is_unread", unread));
    }

    /** {@code escalated.agent.notifications.index}: {@code {data, meta}}, newest first. */
    @GetMapping("/notifications")
    public ResponseEntity<?> notifications(@RequestParam Map<String, String> query, Authentication authentication) {
        Long agentId = agentId(authentication);
        if (!panel.notificationsEnabled() || agentId == null) {
            return ResponseEntity.status(404).build();
        }
        int perPage = clamp(query.get("per_page"), 20, 1, 50);
        int page = clamp(query.get("page"), 1, 1, Integer.MAX_VALUE);
        boolean unreadOnly = "1".equals(query.get("unread")) || "true".equalsIgnoreCase(query.get("unread"));
        return ResponseEntity.ok(panel.page(agentId, page, perPage, unreadOnly));
    }

    /** {@code escalated.agent.notifications.read}: {@code {unread_count}}; another agent's is a 404. */
    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<?> readNotification(@PathVariable("id") String id, Authentication authentication) {
        Long agentId = agentId(authentication);
        if (!panel.notificationsEnabled() || agentId == null || !panel.read(agentId, id)) {
            return ResponseEntity.status(404).build();
        }
        return ResponseEntity.ok(Map.of("unread_count", panel.unreadCount(agentId)));
    }

    /** {@code escalated.agent.notifications.read-all}: {@code {unread_count: 0}}. */
    @PostMapping("/notifications/read-all")
    public ResponseEntity<?> readAll(Authentication authentication) {
        Long agentId = agentId(authentication);
        if (!panel.notificationsEnabled() || agentId == null) {
            return ResponseEntity.status(404).build();
        }
        panel.readAll(agentId);
        return ResponseEntity.ok(Map.of("unread_count", 0));
    }

    private static int clamp(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }
}
