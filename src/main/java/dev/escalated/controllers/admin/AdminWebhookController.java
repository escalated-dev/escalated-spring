package dev.escalated.controllers.admin;

import dev.escalated.models.Webhook;
import dev.escalated.models.WebhookDelivery;
import dev.escalated.services.WebhookService;
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
@RequestMapping("/escalated/api/admin/webhooks")
public class AdminWebhookController {

    private final WebhookService webhookService;

    public AdminWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping
    public ResponseEntity<List<Webhook>> index() {
        return ResponseEntity.ok(webhookService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Webhook> show(@PathVariable Long id) {
        return ResponseEntity.ok(webhookService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Webhook> store(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(201).body(webhookService.create(
                body.get("url"), body.get("secret"), body.get("events"), body.get("description")));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Webhook> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(webhookService.update(id,
                (String) body.get("url"),
                (String) body.get("events"),
                (String) body.get("description"),
                Boolean.parseBoolean(body.getOrDefault("active", "true").toString())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> destroy(@PathVariable Long id) {
        webhookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/deliveries")
    public ResponseEntity<List<WebhookDelivery>> deliveries(@PathVariable Long id) {
        return ResponseEntity.ok(webhookService.getDeliveries(id));
    }
}
