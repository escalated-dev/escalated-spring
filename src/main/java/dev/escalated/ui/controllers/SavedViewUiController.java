package dev.escalated.ui.controllers;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.ValidationFailedException;
import dev.escalated.ui.AdminTicketUiPages;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The saved ticket views the admin queue's sidebar and save dialog call with {@code fetch}, as
 * escalated-laravel's {@code Admin\SavedViewController}: JSON in and out, not Inertia. A view
 * someone else saved can be applied but not changed (403).
 */
@RestController
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin/saved-views")
public class SavedViewUiController {

    private final AdminTicketUiPages pages;
    private final AgentProfileRepository agents;

    public SavedViewUiController(AdminTicketUiPages pages, AgentProfileRepository agents) {
        this.pages = pages;
        this.agents = agents;
    }

    @GetMapping
    public List<Map<String, Object>> index(Authentication authentication) {
        return pages.savedViews(agentId(authentication));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(HttpServletRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pages.storeSavedView(UiInput.of(request), agentId(authentication)));
    }

    @PutMapping("/{view:\\d+}")
    public Map<String, Object> update(@PathVariable("view") Long id, HttpServletRequest request,
                                      Authentication authentication) {
        return pages.updateSavedView(id, UiInput.of(request), agentId(authentication));
    }

    @DeleteMapping("/{view:\\d+}")
    public Map<String, Object> destroy(@PathVariable("view") Long id, Authentication authentication) {
        pages.deleteSavedView(id, agentId(authentication));
        return Map.of("message", "View deleted.");
    }

    /** As Laravel answers a JSON request that fails validation: 422 with {@code message} and {@code errors}. */
    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<Map<String, Object>> invalid(ValidationFailedException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        String first = exception.getErrors().values().stream().flatMap(List::stream).findFirst()
                .orElse("The given data was invalid.");
        body.put("message", first);
        body.put("errors", exception.getErrors());
        return ResponseEntity.unprocessableEntity().body(body);
    }

    private Long agentId(Authentication authentication) {
        return agents.findByEmail(authentication.getName()).map(AgentProfile::getId).orElse(null);
    }
}
