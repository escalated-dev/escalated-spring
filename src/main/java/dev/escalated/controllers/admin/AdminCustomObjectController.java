package dev.escalated.controllers.admin;

import dev.escalated.services.CustomObjectService;
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
 * Custom objects and their records over JSON, in the shapes the admin pages read. Invalid input
 * answers 422; an unknown object, or a record of another object, 404.
 */
@RestController
@RequestMapping("/escalated/api/admin/custom-objects")
public class AdminCustomObjectController {

    private final CustomObjectService objects;

    public AdminCustomObjectController(CustomObjectService objects) {
        this.objects = objects;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> index() {
        return ResponseEntity.ok(objects.index());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> show(@PathVariable Long id) {
        return ResponseEntity.ok(objects.show(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(CustomObjectService.row(objects.save(null, body)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(CustomObjectService.row(objects.save(id, body)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> destroy(@PathVariable Long id) {
        objects.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/records")
    public ResponseEntity<List<Map<String, Object>>> records(@PathVariable Long id) {
        return ResponseEntity.ok(objects.records(id));
    }

    @PostMapping("/{id}/records")
    public ResponseEntity<Map<String, Object>> storeRecord(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(CustomObjectService.record(objects.saveRecord(id, null, body)));
    }

    @PutMapping("/{id}/records/{record}")
    public ResponseEntity<Map<String, Object>> updateRecord(@PathVariable Long id, @PathVariable Long record,
                                                            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(CustomObjectService.record(objects.saveRecord(id, record, body)));
    }

    @DeleteMapping("/{id}/records/{record}")
    public ResponseEntity<Void> destroyRecord(@PathVariable Long id, @PathVariable Long record) {
        objects.deleteRecord(id, record);
        return ResponseEntity.noContent().build();
    }
}
