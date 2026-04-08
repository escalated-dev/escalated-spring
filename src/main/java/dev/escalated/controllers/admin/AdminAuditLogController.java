package dev.escalated.controllers.admin;

import dev.escalated.models.AuditLog;
import dev.escalated.services.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/admin/audit-logs")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<Page<AuditLog>> index(@RequestParam(required = false) String actorEmail,
                                                @PageableDefault(size = 50) Pageable pageable) {
        if (actorEmail != null) {
            return ResponseEntity.ok(auditLogService.findByActor(actorEmail, pageable));
        }
        return ResponseEntity.ok(auditLogService.findAll(pageable));
    }
}
