package dev.escalated.controllers.admin;

import dev.escalated.services.ImportService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/admin/import")
public class AdminImportController {

    private final ImportService importService;

    public AdminImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/tickets")
    public ResponseEntity<ImportService.ImportResult> importTickets(
            @RequestBody List<Map<String, String>> rows,
            @RequestParam(defaultValue = "admin") String actorEmail) {
        return ResponseEntity.ok(importService.importTickets(rows, actorEmail));
    }
}
