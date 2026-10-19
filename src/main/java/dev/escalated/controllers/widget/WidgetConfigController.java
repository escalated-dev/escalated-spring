package dev.escalated.controllers.widget;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Department;
import dev.escalated.services.DepartmentService;
import dev.escalated.services.SettingsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the widget needs before it renders. Colour, position and greeting come
 * from the {@code widget_color}, {@code widget_position} and
 * {@code widget_greeting} settings; {@code escalated.widget.enabled=false}
 * answers 403 with {@code {"enabled": false}}.
 */
@RestController
@RequestMapping("/escalated/api/widget")
public class WidgetConfigController {

    private final EscalatedProperties properties;
    private final SettingsService settings;
    private final DepartmentService departments;

    public WidgetConfigController(EscalatedProperties properties, SettingsService settings, DepartmentService departments) {
        this.properties = properties;
        this.settings = settings;
        this.departments = departments;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        if (!properties.getWidget().isEnabled()) {
            return ResponseEntity.status(403).body(Map.of("enabled", false));
        }

        List<Map<String, Object>> active = departments.findAll().stream()
                .filter(Department::isActive)
                .map(department -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", department.getId());
                    entry.put("name", department.getName());
                    return entry;
                })
                .toList();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("color", settings.getOrDefault("widget_color", "#4F46E5"));
        config.put("position", settings.getOrDefault("widget_position", "bottom-right"));
        config.put("greeting", settings.getOrDefault("widget_greeting", "Hi there! How can we help?"));
        config.put("departments", active);
        config.put("kb_enabled", properties.getKnowledgeBase().isEnabled());
        // POST /tickets always accepts guest tickets.
        config.put("guest_tickets_enabled", true);
        // Tickets and chats keep host parameters sent as `metadata`.
        config.put("accepts_metadata", true);
        // No mailbox verification here, so no status lookup by email: the
        // widget hides its Status tab. Guests follow a ticket by its token.
        config.put("status_lookup_enabled", false);
        return ResponseEntity.ok(config);
    }
}
