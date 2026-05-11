package dev.escalated.controllers.admin;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.UserService;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin users-management endpoint. Lists users (host agent profiles) with
 * their admin / agent flags and lets an admin grant or revoke either
 * role. Backs the {@code Escalated/Admin/Users/Index} Inertia page shipped
 * with the shared {@code @escalated-dev/escalated} frontend package.
 */
@RestController
@RequestMapping("/escalated/api/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final AgentProfileRepository agentRepository;

    public AdminUserController(UserService userService, AgentProfileRepository agentRepository) {
        this.userService = userService;
        this.agentRepository = agentRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> index(
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {

        Page<AgentProfile> page = userService.search(search, pageable);

        List<Map<String, Object>> data = page.getContent().stream()
                .map(AdminUserController::toJson)
                .toList();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("current_page", page.getNumber() + 1);
        meta.put("per_page", page.getSize());
        meta.put("total", page.getTotalElements());
        meta.put("last_page", Math.max(1, page.getTotalPages()));

        Map<String, Object> users = new LinkedHashMap<>();
        users.put("data", data);
        users.put("meta", meta);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("users", users);
        props.put("filters", Map.of("search", search == null ? "" : search));
        props.put("currentUserId", currentUserId(authentication));

        return ResponseEntity.ok(props);
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<Map<String, Object>> updateRole(@PathVariable Long userId,
                                                          @RequestBody UpdateRoleRequest body,
                                                          Authentication authentication) {
        if (body == null || body.getRole() == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Invalid role.");
            return ResponseEntity.badRequest().body(err);
        }

        try {
            AgentProfile updated = userService.updateRole(
                    userId,
                    body.getRole(),
                    body.isValue(),
                    currentUserId(authentication));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("user", toJson(updated));
            response.put("message", "User updated.");
            return ResponseEntity.ok(response);
        } catch (UserService.SelfDemoteException ex) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", ex.getMessage());
            return ResponseEntity.unprocessableEntity().body(err);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    private static Map<String, Object> toJson(AgentProfile user) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", user.getId());
        json.put("name", user.getName());
        json.put("email", user.getEmail());
        json.put("is_admin", user.isAdmin());
        json.put("is_agent", user.isAgent());
        return json;
    }

    /**
     * Resolve the calling user's profile id from the security context. The
     * filter chain (see {@code ApiTokenAuthenticationFilter}) sets the
     * principal to the agent email; we look up the profile by email to get
     * the numeric id we compare against the target.
     */
    private Long currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return agentRepository.findByEmail(authentication.getName())
                .map(AgentProfile::getId)
                .orElse(null);
    }

    /** Request body for {@code PATCH /admin/users/{userId}/role}. */
    public static class UpdateRoleRequest {
        private String role;
        private boolean value;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public boolean isValue() {
            return value;
        }

        public void setValue(boolean value) {
            this.value = value;
        }
    }
}
