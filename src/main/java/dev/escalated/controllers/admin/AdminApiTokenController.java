package dev.escalated.controllers.admin;

import dev.escalated.services.ApiTokenAdminService;
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

/** Admin API for Escalated API tokens. The plain-text token is answered once, on creation. */
@RestController
@RequestMapping("/escalated/api/admin/tokens")
public class AdminApiTokenController {

    private final ApiTokenAdminService tokens;

    public AdminApiTokenController(ApiTokenAdminService tokens) {
        this.tokens = tokens;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> index() {
        return ResponseEntity.ok(tokens.list());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.status(201)
                .header("Cache-Control", "no-store")
                .body(tokens.issue(body));
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(tokens.update(id, body));
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> destroy(@PathVariable Long id) {
        tokens.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
