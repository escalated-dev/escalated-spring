package dev.escalated.controllers.admin;

import dev.escalated.services.AutomationAdminService;
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
import org.springframework.web.bind.annotation.RestController;

/** Admin JSON API for time-based automations. */
@RestController
@RequestMapping("/escalated/api/admin/automations")
public class AdminAutomationController {

    private final AutomationAdminService automations;

    public AdminAutomationController(AutomationAdminService automations) {
        this.automations = automations;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> index() {
        return ResponseEntity.ok(automations.list());
    }

    @GetMapping({"/options", "/create"})
    public ResponseEntity<Map<String, Object>> options() {
        return ResponseEntity.ok(automations.options());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(201).body(automations.create(body));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> show(@PathVariable Long id) {
        return ResponseEntity.ok(automations.show(id));
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(automations.update(id, body));
    }

    @PostMapping("/{id:\\d+}/toggle")
    public ResponseEntity<Map<String, Object>> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(automations.toggle(id));
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> destroy(@PathVariable Long id) {
        automations.delete(id);
        return ResponseEntity.noContent().build();
    }
}
