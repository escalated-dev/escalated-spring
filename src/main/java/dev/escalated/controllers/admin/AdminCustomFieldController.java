package dev.escalated.controllers.admin;

import dev.escalated.models.CustomField;
import dev.escalated.services.CustomFieldService;
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

@RestController
@RequestMapping("/escalated/api/admin/custom-fields")
public class AdminCustomFieldController {

    private final CustomFieldService customFieldService;

    public AdminCustomFieldController(CustomFieldService customFieldService) {
        this.customFieldService = customFieldService;
    }

    @GetMapping
    public ResponseEntity<List<CustomField>> index() {
        return ResponseEntity.ok(customFieldService.findActiveFields());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomField> show(@PathVariable Long id) {
        return ResponseEntity.ok(customFieldService.findById(id));
    }

    @PostMapping
    public ResponseEntity<CustomField> store(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(customFieldService.create(
                (String) body.get("name"),
                (String) body.get("field_key"),
                (String) body.getOrDefault("field_type", "text"),
                (String) body.get("description"),
                Boolean.parseBoolean(body.getOrDefault("required", "false").toString()),
                (String) body.get("options")));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomField> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(customFieldService.update(id,
                (String) body.get("name"),
                (String) body.get("description"),
                Boolean.parseBoolean(body.getOrDefault("required", "false").toString()),
                Boolean.parseBoolean(body.getOrDefault("active", "true").toString())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> destroy(@PathVariable Long id) {
        customFieldService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
