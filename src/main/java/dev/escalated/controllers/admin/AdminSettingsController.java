package dev.escalated.controllers.admin;

import dev.escalated.models.EscalatedSettings;
import dev.escalated.services.SettingsService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/admin/settings")
public class AdminSettingsController {

    private final SettingsService settingsService;

    public AdminSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<List<EscalatedSettings>> index(@RequestParam(required = false) String group) {
        if (group != null) {
            return ResponseEntity.ok(settingsService.findByGroup(group));
        }
        return ResponseEntity.ok(settingsService.findAll());
    }

    @PostMapping
    public ResponseEntity<Void> store(@RequestBody Map<String, String> body) {
        settingsService.set(body.get("key"), body.get("value"), body.get("group"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> destroy(@PathVariable String key) {
        settingsService.delete(key);
        return ResponseEntity.noContent().build();
    }
}
