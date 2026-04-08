package dev.escalated.controllers.admin;

import dev.escalated.models.Permission;
import dev.escalated.models.Role;
import dev.escalated.services.RolePermissionService;
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
@RequestMapping("/escalated/api/admin/roles")
public class AdminRoleController {

    private final RolePermissionService rolePermissionService;

    public AdminRoleController(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping
    public ResponseEntity<List<Role>> index() {
        return ResponseEntity.ok(rolePermissionService.findAllRoles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Role> show(@PathVariable Long id) {
        return ResponseEntity.ok(rolePermissionService.findRoleById(id));
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Role> store(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(rolePermissionService.createRole(
                (String) body.get("name"),
                (String) body.get("description"),
                (List<Long>) body.get("permission_ids")));
    }

    @PutMapping("/{id}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Role> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(rolePermissionService.updateRole(id,
                (String) body.get("name"),
                (String) body.get("description"),
                (List<Long>) body.get("permission_ids")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> destroy(@PathVariable Long id) {
        rolePermissionService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<Permission>> permissions() {
        return ResponseEntity.ok(rolePermissionService.findAllPermissions());
    }
}
