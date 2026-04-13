package dev.escalated.controllers.admin;

import dev.escalated.models.Workflow;
import dev.escalated.models.WorkflowLog;
import dev.escalated.repositories.WorkflowLogRepository;
import dev.escalated.repositories.WorkflowRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/admin/workflows")
public class AdminWorkflowController {

    private final WorkflowRepository workflowRepository;
    private final WorkflowLogRepository workflowLogRepository;

    public AdminWorkflowController(WorkflowRepository workflowRepository,
                                    WorkflowLogRepository workflowLogRepository) {
        this.workflowRepository = workflowRepository;
        this.workflowLogRepository = workflowLogRepository;
    }

    @GetMapping
    public ResponseEntity<List<Workflow>> index() {
        return ResponseEntity.ok(workflowRepository.findAllByOrderByPositionAscNameAsc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Workflow> show(@PathVariable Long id) {
        return workflowRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<List<WorkflowLog>> logs(@PathVariable Long id) {
        return ResponseEntity.ok(workflowLogRepository.findByWorkflowIdWithRelations(id));
    }
}
