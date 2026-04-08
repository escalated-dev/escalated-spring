package dev.escalated.controllers.admin;

import dev.escalated.models.AgentProfile;
import dev.escalated.services.AgentService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/admin/agents")
public class AdminAgentController {

    private final AgentService agentService;

    public AdminAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public ResponseEntity<List<AgentProfile>> index() {
        return ResponseEntity.ok(agentService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentProfile> show(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.findById(id));
    }

    @PostMapping
    public ResponseEntity<AgentProfile> store(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(agentService.create(
                (String) body.get("name"),
                (String) body.get("email"),
                body.get("department_id") != null ? Long.valueOf(body.get("department_id").toString()) : null,
                body.get("role_id") != null ? Long.valueOf(body.get("role_id").toString()) : null
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgentProfile> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(agentService.update(id,
                (String) body.get("name"),
                (String) body.get("phone"),
                (String) body.get("signature"),
                Boolean.parseBoolean(body.getOrDefault("available", "true").toString())));
    }

    @PutMapping("/{id}/capacity")
    public ResponseEntity<Void> updateCapacity(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        agentService.updateCapacity(id, body.getOrDefault("max_tickets", 20), body.getOrDefault("weight", 1));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/skills/{skillId}")
    public ResponseEntity<Void> addSkill(@PathVariable Long id, @PathVariable Long skillId) {
        agentService.addSkill(id, skillId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/skills/{skillId}/remove")
    public ResponseEntity<Void> removeSkill(@PathVariable Long id, @PathVariable Long skillId) {
        agentService.removeSkill(id, skillId);
        return ResponseEntity.ok().build();
    }
}
