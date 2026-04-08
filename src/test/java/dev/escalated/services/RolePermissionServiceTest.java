package dev.escalated.services;

import dev.escalated.models.Permission;
import dev.escalated.models.Role;
import dev.escalated.repositories.PermissionRepository;
import dev.escalated.repositories.RoleRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;

    private RolePermissionService rolePermissionService;

    @BeforeEach
    void setUp() {
        rolePermissionService = new RolePermissionService(roleRepository, permissionRepository);
    }

    @Test
    void createRole_shouldSaveWithPermissions() {
        Permission perm = new Permission();
        perm.setId(1L);
        perm.setName("tickets.view");

        when(permissionRepository.findById(1L)).thenReturn(Optional.of(perm));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        Role result = rolePermissionService.createRole("custom", "Custom role", List.of(1L));

        assertEquals("custom", result.getName());
        assertTrue(result.getPermissions().contains(perm));
    }

    @Test
    void updateRole_shouldRejectSystemRole() {
        Role role = new Role();
        role.setId(1L);
        role.setName("admin");
        role.setSystem(true);

        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThrows(IllegalStateException.class,
                () -> rolePermissionService.updateRole(1L, "modified", "desc", null));
    }

    @Test
    void hasPermission_shouldCheckRolePermissions() {
        Permission perm = new Permission();
        perm.setId(1L);
        perm.setName("tickets.view");

        Role role = new Role();
        role.setId(1L);
        role.setPermissions(Set.of(perm));

        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertTrue(rolePermissionService.hasPermission(1L, "tickets.view"));
    }
}
