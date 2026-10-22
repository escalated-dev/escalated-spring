package dev.escalated.controllers.admin;

import dev.escalated.models.CustomField;
import dev.escalated.services.CustomFieldService;
import java.util.LinkedHashMap;
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
 * Custom field definitions over JSON. Takes escalated-laravel's keys ({@code name, type, context,
 * options, required, placeholder, description, validation_rules, conditions, position, active},
 * optional {@code slug}) and this API's older ones ({@code field_key} for the slug,
 * {@code field_type} for the type). Invalid input answers 422.
 */
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
        Map<String, Object> input = laravelKeys(body);
        input.putIfAbsent("type", "text");
        return ResponseEntity.status(201).body(customFieldService.save(null, input));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomField> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(customFieldService.save(id, laravelKeys(body)));
    }

    @PostMapping("/reorder")
    public ResponseEntity<Void> reorder(@RequestBody Map<String, Object> body) {
        customFieldService.reorder(body.get("positions"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> destroy(@PathVariable Long id) {
        customFieldService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> laravelKeys(Map<String, Object> body) {
        Map<String, Object> input = new LinkedHashMap<>(body);
        if (!input.containsKey("slug") && input.containsKey("field_key")) {
            input.put("slug", input.get("field_key"));
        }
        if (!input.containsKey("type") && input.containsKey("field_type")) {
            input.put("type", input.get("field_type"));
        }
        if (!input.containsKey("position") && input.containsKey("sort_order")) {
            input.put("position", input.get("sort_order"));
        }
        return input;
    }
}
