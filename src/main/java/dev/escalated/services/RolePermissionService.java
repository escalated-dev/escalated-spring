package dev.escalated.services;

import dev.escalated.models.Permission;
import dev.escalated.models.Role;
import dev.escalated.repositories.PermissionRepository;
import dev.escalated.repositories.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RolePermissionService(RoleRepository roleRepository,
                                 PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<Role> findAllRoles() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Role findRoleById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));
    }

    @Transactional
    public Role createRole(String name, String description, List<Long> permissionIds) {
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);

        if (permissionIds != null && !permissionIds.isEmpty()) {
            Set<Permission> permissions = permissionIds.stream()
                    .map(pid -> permissionRepository.findById(pid)
                            .orElseThrow(() -> new EntityNotFoundException("Permission not found: " + pid)))
                    .collect(Collectors.toSet());
            role.setPermissions(permissions);
        }

        return roleRepository.save(role);
    }

    @Transactional
    public Role updateRole(Long id, String name, String description, List<Long> permissionIds) {
        Role role = findRoleById(id);
        if (role.isSystem()) {
            throw new IllegalStateException("Cannot modify system role: " + role.getName());
        }
        role.setName(name);
        role.setDescription(description);

        if (permissionIds != null) {
            Set<Permission> permissions = permissionIds.stream()
                    .map(pid -> permissionRepository.findById(pid)
                            .orElseThrow(() -> new EntityNotFoundException("Permission not found: " + pid)))
                    .collect(Collectors.toSet());
            role.setPermissions(permissions);
        }

        return roleRepository.save(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = findRoleById(id);
        if (role.isSystem()) {
            throw new IllegalStateException("Cannot delete system role: " + role.getName());
        }
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public List<Permission> findAllPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional
    public Permission createPermission(String name, String description, String category) {
        Permission permission = new Permission();
        permission.setName(name);
        permission.setDescription(description);
        permission.setCategory(category);
        return permissionRepository.save(permission);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long roleId, String permissionName) {
        Role role = findRoleById(roleId);
        return role.hasPermission(permissionName);
    }
}
