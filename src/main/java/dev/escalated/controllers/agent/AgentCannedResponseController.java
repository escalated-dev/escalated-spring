package dev.escalated.controllers.agent;

import dev.escalated.models.CannedResponse;
import dev.escalated.services.CannedResponseService;
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
@RequestMapping("/escalated/api/agent/canned-responses")
public class AgentCannedResponseController {

    private final CannedResponseService cannedResponseService;

    public AgentCannedResponseController(CannedResponseService cannedResponseService) {
        this.cannedResponseService = cannedResponseService;
    }

    @GetMapping
    public ResponseEntity<List<CannedResponse>> index(@RequestParam Long agentId) {
        return ResponseEntity.ok(cannedResponseService.findAccessibleByAgent(agentId));
    }

    @PostMapping
    public ResponseEntity<CannedResponse> store(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(cannedResponseService.create(
                (String) body.get("title"),
                (String) body.get("content"),
                (String) body.get("shortcut"),
                (String) body.get("category"),
                Boolean.parseBoolean(body.getOrDefault("shared", "true").toString()),
                body.get("agent_id") != null ? Long.valueOf(body.get("agent_id").toString()) : null));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CannedResponse> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(cannedResponseService.update(id,
                body.get("title"), body.get("content"), body.get("shortcut"), body.get("category")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> destroy(@PathVariable Long id) {
        cannedResponseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
