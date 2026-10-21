package dev.escalated.ui;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Permission;
import dev.escalated.models.Role;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.PermissionRepository;
import dev.escalated.repositories.RoleRepository;
import dev.escalated.services.ApiTokenAdminService;
import dev.escalated.services.RolePermissionService;
import dev.escalated.services.UserService;
import jakarta.persistence.EntityNotFoundException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The people pages' props and writes: users (agent and admin flags), roles and API tokens, as
 * escalated-laravel's {@code Admin\UserController}, {@code RoleController} and
 * {@code ApiTokenController}. Built field by field: no two-factor secret, password or token hash
 * reaches a page; a new token's plain text is flashed once.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class AdminPeopleUiPages {

    private static final int USERS_PER_PAGE = 20;

    private final UserService users;
    private final AgentProfileRepository agents;
    private final RolePermissionService roleService;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final ApiTokenAdminService tokens;
    private final UiRoutes routes;

    public AdminPeopleUiPages(UserService users, AgentProfileRepository agents, RolePermissionService roleService,
                              RoleRepository roles, PermissionRepository permissions, ApiTokenAdminService tokens,
                              UiRoutes routes) {
        this.users = users;
        this.agents = agents;
        this.roleService = roleService;
        this.roles = roles;
        this.permissions = permissions;
        this.tokens = tokens;
        this.routes = routes;
    }

    // ── Users ───────────────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> users(String search, int page, Long currentUserId) {
        String term = search == null ? "" : search.trim();
        Page<AgentProfile> found = users.search(term, PageRequest.of(Math.max(page, 1) - 1, USERS_PER_PAGE));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgentProfile user : found) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("name", user.getName());
            row.put("email", user.getEmail());
            row.put("is_admin", user.isAdmin());
            row.put("is_agent", user.isAgent());
            rows.add(row);
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("users", UiPresenter.paginator(found, rows, routes.url("escalated.admin.users.index")));
        props.put("filters", Map.of("search", term));
        props.put("currentUserId", currentUserId);
        return props;
    }

    /** Validates as Laravel's {@code UserController::updateRole}; self-demotion is the service's refusal. */
    public void updateUserRole(Long userId, UiInput input, Long currentUserId) {
        String role = input.string("role");
        if (role == null) {
            throw UiInput.invalid("role", "The role field is required.");
        }
        if (!"admin".equals(role) && !"agent".equals(role)) {
            throw UiInput.invalid("role", "The selected role is invalid.");
        }
        Object value = input.raw("value");
        if (value == null || !List.of("true", "false", "1", "0").contains(String.valueOf(value).toLowerCase())) {
            throw UiInput.invalid("value", value == null ? "The value field is required." : "The value field must be true or false.");
        }
        users.updateRole(userId, role, input.bool("value", false), currentUserId);
    }

    // ── Roles ───────────────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> roles() {
        List<Role> all = new ArrayList<>(roles.findAll());
        all.sort(Comparator.comparing(Role::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Role role : all) {
            Map<String, Object> row = role(role);
            row.put("users_count", role.getAgents().size());
            rows.add(row);
        }
        return Map.of("roles", rows);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> roleForm(Long id) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (id != null) {
            Role role = findRole(id);
            Map<String, Object> shown = role(role);
            List<Map<String, Object>> granted = new ArrayList<>();
            role.getPermissions().stream().sorted(Comparator.comparing(Permission::getId))
                    .forEach(permission -> granted.add(permission(permission)));
            shown.put("permissions", granted);
            props.put("role", shown);
        }
        props.put("permissions", permissionsByGroup());
        return props;
    }

    /** Validates as Laravel's {@code RoleController::store}/{@code update} and saves. */
    public void saveRole(Long id, UiInput input) {
        String name = input.requiredString("name", 255);
        String description = input.string("description");
        List<Long> permissionIds = input.has("permissions") ? input.longs("permissions") : List.of();
        for (Long permissionId : permissionIds) {
            if (!permissions.existsById(permissionId)) {
                throw UiInput.invalid("permissions", "The selected permissions is invalid.");
            }
        }
        if (id == null) {
            roleService.createRole(name, description, permissionIds);
        } else {
            roleService.updateRole(id, name, description, permissionIds);
        }
    }

    /** Deletes a role; its agents are left without one. System roles stay ({@link IllegalStateException}). */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteRole(Long id) {
        Role role = findRole(id);
        if (role.isSystem()) {
            throw new IllegalStateException("System roles cannot be deleted.");
        }
        for (AgentProfile agent : new ArrayList<>(role.getAgents())) {
            agent.setRole(null);
            agents.save(agent);
        }
        roles.delete(role);
        roles.flush();
    }

    public boolean isSystemRole(Long id) {
        return roles.findById(id).map(Role::isSystem).orElseThrow(() -> new EntityNotFoundException("Role not found"));
    }

    private Map<String, List<Map<String, Object>>> permissionsByGroup() {
        Map<String, List<Map<String, Object>>> groups = new TreeMap<>();
        List<Permission> all = new ArrayList<>(permissions.findAll());
        all.sort(Comparator.comparing(Permission::getId));
        for (Permission permission : all) {
            String group = permission.getCategory() == null ? "general" : permission.getCategory();
            groups.computeIfAbsent(group, key -> new ArrayList<>()).add(permission(permission));
        }
        return groups;
    }

    private static Map<String, Object> role(Role role) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", role.getId());
        map.put("name", role.getName());
        map.put("description", role.getDescription());
        map.put("is_system", role.isSystem());
        return map;
    }

    private static Map<String, Object> permission(Permission permission) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", permission.getId());
        map.put("name", permission.getName());
        map.put("slug", permission.getName());
        map.put("group", permission.getCategory());
        map.put("description", permission.getDescription());
        return map;
    }

    private Role findRole(Long id) {
        return roles.findById(id).orElseThrow(() -> new EntityNotFoundException("Role not found"));
    }

    // ── API tokens ──────────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> apiTokens() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> token : tokens.list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String key : List.of("id", "name", "user_name", "user_email", "abilities", "last_used_at",
                    "expires_at", "is_expired", "created_at")) {
                Object value = token.get(key);
                row.put(key, value instanceof TemporalAccessor ? value.toString() : value);
            }
            row.put("last_used_ip", null);
            rows.add(row);
        }
        List<Map<String, Object>> staff = new ArrayList<>();
        for (AgentProfile agent : agents.findByActiveTrueOrderByName()) {
            if (agent.isAgent() || agent.isAdmin()) {
                staff.add(UiPresenter.agent(agent));
            }
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("tokens", rows);
        props.put("users", staff);
        props.put("api_enabled", true);
        return props;
    }

    /** Issues a token and answers its plain text, which is shown once. */
    public String issueToken(UiInput input) {
        Map<String, Object> body = new LinkedHashMap<>(input.all());
        Object expires = body.get("expires_in_days");
        if (expires instanceof String text && text.isBlank()) {
            body.remove("expires_in_days");
        }
        if (!body.containsKey("abilities")) {
            throw UiInput.invalid("abilities", "The abilities field is required.");
        }
        return String.valueOf(tokens.issue(body).get("plain_text_token"));
    }

    public void updateToken(Long id, UiInput input) {
        tokens.update(id, input.all());
    }

    public void revokeToken(Long id) {
        tokens.revoke(id);
    }
}
