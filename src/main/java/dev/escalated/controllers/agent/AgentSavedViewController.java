package dev.escalated.controllers.agent;

import dev.escalated.models.SavedView;
import dev.escalated.services.SavedViewService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/agent/saved-views")
public class AgentSavedViewController {

    private final SavedViewService savedViewService;

    public AgentSavedViewController(SavedViewService savedViewService) {
        this.savedViewService = savedViewService;
    }

    @GetMapping
    public ResponseEntity<List<SavedView>> index(@RequestParam Long agentId) {
        return ResponseEntity.ok(savedViewService.findAccessibleByAgent(agentId));
    }

    @PostMapping
    public ResponseEntity<SavedView> store(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(savedViewService.create(
                (String) body.get("name"),
                (String) body.get("filters"),
                (String) body.get("sort_by"),
                (String) body.getOrDefault("sort_direction", "desc"),
                (String) body.get("columns"),
                Boolean.parseBoolean(body.getOrDefault("shared", "false").toString()),
                body.get("agent_id") != null ? Long.valueOf(body.get("agent_id").toString()) : null));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavedView> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(savedViewService.update(id,
                (String) body.get("name"),
                (String) body.get("filters"),
                (String) body.get("sort_by"),
                (String) body.getOrDefault("sort_direction", "desc"),
                (String) body.get("columns"),
                Boolean.parseBoolean(body.getOrDefault("shared", "false").toString())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> destroy(@PathVariable Long id) {
        savedViewService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
