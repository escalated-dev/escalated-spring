package dev.escalated.ui.controllers;

import dev.escalated.services.AgentPanelService;
import dev.escalated.ui.UiRoutes;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code escalated.admin.agents.search}, the reply composer's mention picker, as escalated-laravel's
 * {@code AgentSearchController}: up to 10 agents and admins whose name or email contains {@code q},
 * as {@code [{id, name, email}]}; {@code []} without a query. Admins only (the web security chain),
 * plus {@code ticket.view} while roles and permissions are on.
 */
@RestController
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin")
public class AdminAgentSearchUiController {

    private final AgentPanelService panel;

    public AdminAgentSearchUiController(AgentPanelService panel) {
        this.panel = panel;
    }

    @GetMapping("/agents/search")
    public List<Map<String, Object>> search(@RequestParam(name = "q", required = false) String q) {
        return panel.mentionSuggestions(q);
    }
}
