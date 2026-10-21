package dev.escalated.controllers.admin;

import dev.escalated.models.WorkflowLog;
import dev.escalated.repositories.WorkflowLogRepository;
import dev.escalated.services.WorkflowAdminService;
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

/**
 * Workflow admin JSON API, per escalated-developer-context
 * {@code domain-model/workflow-admin-contract.md}. The bodies are what the
 * shared builder posts; answers are workflow objects. This port has no
 * Inertia layer, so writes answer JSON rather than redirecting.
 */
@RestController
@RequestMapping("/escalated/api/admin/workflows")
public class AdminWorkflowController {

    private final WorkflowAdminService workflows;
    private final WorkflowLogRepository workflowLogRepository;

    public AdminWorkflowController(WorkflowAdminService workflows,
                                   WorkflowLogRepository workflowLogRepository) {
        this.workflows = workflows;
        this.workflowLogRepository = workflowLogRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> index() {
        return ResponseEntity.ok(workflows.list());
    }

    /** The {@code Form} page's option lists: trigger events, action types, operators, condition fields. */
    @GetMapping({"/options", "/create"})
    public ResponseEntity<Map<String, Object>> options() {
        return ResponseEntity.ok(workflows.options());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(201).body(workflows.create(body));
    }

    @PostMapping("/reorder")
    public ResponseEntity<List<Map<String, Object>>> reorder(@RequestBody(required = false) Map<String, Object> body) {
        Object ids = body == null ? null : body.get("workflow_ids");
        return ResponseEntity.ok(workflows.reorder(ids instanceof List<?> list ? list : null));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> show(@PathVariable Long id) {
        return ResponseEntity.ok(workflows.show(id));
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(workflows.update(id, body));
    }

    @PostMapping("/{id:\\d+}/toggle")
    public ResponseEntity<Map<String, Object>> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(workflows.toggle(id));
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> destroy(@PathVariable Long id) {
        workflows.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id:\\d+}/logs")
    public ResponseEntity<List<WorkflowLog>> logs(@PathVariable Long id) {
        return ResponseEntity.ok(workflowLogRepository.findByWorkflowIdWithRelations(id));
    }
}
