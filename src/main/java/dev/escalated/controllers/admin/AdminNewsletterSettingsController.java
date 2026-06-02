package dev.escalated.controllers.admin;

import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.assertEmail;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.inertia;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.optionalString;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.redirect;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.requiredBoolean;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.requiredInteger;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.requiredString;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.EscalatedSettings;
import dev.escalated.repositories.EscalatedSettingsRepository;
import dev.escalated.services.newsletter.NewsletterPermissionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/admin/newsletters/settings")
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class AdminNewsletterSettingsController {

    private static final List<String> KEYS = List.of(
            "default_from",
            "default_reply_to",
            "default_theme",
            "rate_limit_per_minute",
            "batch_size",
            "tracking_enabled");

    private final EscalatedProperties properties;
    private final NewsletterPermissionService permissions;
    private final EscalatedSettingsRepository settings;

    public AdminNewsletterSettingsController(
            EscalatedProperties properties,
            NewsletterPermissionService permissions,
            EscalatedSettingsRepository settings) {
        this.properties = properties;
        this.permissions = permissions;
        this.settings = settings;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> show(Authentication auth) {
        permissions.require(auth, "newsletters.manage");
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : KEYS) {
            values.put(key, load(key));
        }
        return ResponseEntity.ok(inertia(
                "Escalated/Admin/Newsletters/Settings",
                Map.of("settings", values, "themes", List.of("default", "branded"))));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> update(Authentication auth, @RequestBody Map<String, Object> body) {
        permissions.require(auth, "newsletters.manage");
        Map<String, Object> data = Map.of(
                "default_from", assertEmail(optionalString(body, "default_from", 320), "default_from", false),
                "default_reply_to", assertEmail(optionalString(body, "default_reply_to", 320), "default_reply_to", false),
                "default_theme", requiredString(body, "default_theme", 64),
                "rate_limit_per_minute", requiredInteger(body, "rate_limit_per_minute", 1, 10000),
                "batch_size", requiredInteger(body, "batch_size", 1, 1000),
                "tracking_enabled", requiredBoolean(body, "tracking_enabled"));

        for (String key : KEYS) {
            Object value = data.get(key);
            String stored = value instanceof Boolean bool ? String.valueOf(bool ? 1 : 0) : String.valueOf(value ?? "");
            EscalatedSettings row = settings.findByKey("newsletter." + key).orElseGet(() -> {
                EscalatedSettings created = new EscalatedSettings();
                created.setKey("newsletter." + key);
                return created;
            });
            row.setValue(stored);
            row.setGroup("newsletter");
            settings.save(row);
        }
        return ResponseEntity.ok(redirect("/admin/newsletters/settings"));
    }

    private Object load(String key) {
        return settings.findByKey("newsletter." + key)
                .map(EscalatedSettings::getValue)
                .map(value -> parse(key, value))
                .orElseGet(() -> configFallback(key));
    }

    private Object parse(String key, String value) {
        if ("tracking_enabled".equals(key)) {
            return "1".equals(value) || "true".equalsIgnoreCase(value);
        }
        if ("rate_limit_per_minute".equals(key) || "batch_size".equals(key)) {
            return Integer.parseInt(value);
        }
        return value;
    }

    private Object configFallback(String key) {
        EscalatedProperties.NewslettersProperties config = properties.getNewsletters();
        return switch (key) {
            case "default_from" -> config.getDefaultFrom();
            case "default_reply_to" -> config.getDefaultReplyTo();
            case "default_theme" -> config.getDefaultTheme();
            case "rate_limit_per_minute" -> config.getRateLimitPerMinute();
            case "batch_size" -> config.getBatchSize();
            case "tracking_enabled" -> config.isTrackingEnabled();
            default -> null;
        };
    }
}
