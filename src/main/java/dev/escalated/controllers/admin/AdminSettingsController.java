package dev.escalated.controllers.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.escalated.models.EscalatedSettings;
import dev.escalated.services.SettingsService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/admin/settings")
public class AdminSettingsController {

    private static final String PUBLIC_TICKETS_GROUP = "public_tickets";
    private static final String KEY_MODE = "guest_policy_mode";
    private static final String KEY_USER_ID = "guest_policy_user_id";
    private static final String KEY_SIGNUP_URL = "guest_policy_signup_url_template";
    private static final Set<String> VALID_MODES = Set.of("unassigned", "guest_user", "prompt_signup");

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

    // --- Public-ticket guest policy ---
    //
    // Three keys back the policy that decides the identity a public
    // submission is attributed to:
    //   - guest_policy_mode              ∈ { unassigned, guest_user, prompt_signup }
    //   - guest_policy_user_id           required when mode = guest_user
    //   - guest_policy_signup_url_template  optional when mode = prompt_signup
    // Consumers (widget controller, inbound router) read via
    // SettingsService#getOrDefault so admins can switch modes at runtime
    // without a redeploy. Mirrors the Symfony + .NET + Go ports.

    @GetMapping("/public-tickets")
    public ResponseEntity<Map<String, Object>> getPublicTicketsSettings() {
        return ResponseEntity.ok(loadPublicTicketsSettings());
    }

    @PutMapping("/public-tickets")
    public ResponseEntity<Map<String, Object>> updatePublicTicketsSettings(
            @RequestBody PublicTicketsSettingsRequest request) {
        String mode = VALID_MODES.contains(request.guestPolicyMode()) ? request.guestPolicyMode() : "unassigned";
        settingsService.set(KEY_MODE, mode, PUBLIC_TICKETS_GROUP);

        // Clear mode-specific fields we don't need so stale values
        // can't leak back into behavior after a mode switch.
        if ("guest_user".equals(mode) && request.guestPolicyUserId() != null && request.guestPolicyUserId() > 0) {
            settingsService.set(KEY_USER_ID, request.guestPolicyUserId().toString(), PUBLIC_TICKETS_GROUP);
        } else {
            settingsService.set(KEY_USER_ID, "", PUBLIC_TICKETS_GROUP);
        }

        if ("prompt_signup".equals(mode)) {
            String template = request.guestPolicySignupUrlTemplate() == null
                    ? ""
                    : request.guestPolicySignupUrlTemplate().trim();
            if (template.length() > 500) {
                template = template.substring(0, 500);
            }
            settingsService.set(KEY_SIGNUP_URL, template, PUBLIC_TICKETS_GROUP);
        } else {
            settingsService.set(KEY_SIGNUP_URL, "", PUBLIC_TICKETS_GROUP);
        }

        return ResponseEntity.ok(loadPublicTicketsSettings());
    }

    private Map<String, Object> loadPublicTicketsSettings() {
        String mode = settingsService.getOrDefault(KEY_MODE, "unassigned");
        String userIdRaw = settingsService.getOrDefault(KEY_USER_ID, "");
        String template = settingsService.getOrDefault(KEY_SIGNUP_URL, "");

        Map<String, Object> payload = new HashMap<>();
        payload.put("guest_policy_mode", mode);
        payload.put("guest_policy_user_id", parseOptionalPositive(userIdRaw));
        payload.put("guest_policy_signup_url_template", template);
        return payload;
    }

    private static Long parseOptionalPositive(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long n = Long.parseLong(raw.trim());
            return n > 0 ? n : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record PublicTicketsSettingsRequest(
            @JsonProperty("guest_policy_mode") String guestPolicyMode,
            @JsonProperty("guest_policy_user_id") Long guestPolicyUserId,
            @JsonProperty("guest_policy_signup_url_template") String guestPolicySignupUrlTemplate) {
    }
}
