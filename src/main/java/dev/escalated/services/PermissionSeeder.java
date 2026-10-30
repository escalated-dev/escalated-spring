package dev.escalated.services;

import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Permission;
import dev.escalated.models.Role;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.PermissionRepository;
import dev.escalated.repositories.RoleRepository;
import dev.escalated.security.AccessFeatures;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * escalated-laravel's permission set and its system roles ({@code admin}, {@code agent},
 * {@code light_agent}), as its {@code PermissionSeeder}: what roles and permissions enforces (ADR
 * 2026-10-29-configurable-access-features).
 *
 * <p>Idempotent: missing permissions and roles are created and a system role gains the permissions it
 * lacks; nothing is removed. Flyway's {@code V25} seeds the same on PostgreSQL and MySQL. A schema
 * Hibernate builds gets it here, at startup, whenever roles and permissions is on, or from a call to
 * {@link #seed()}. {@link #assignRole(String, String)} gives a user a role, which is how the first
 * admin is bootstrapped (Laravel's {@code escalated:roles:assign}).
 */
@Service
public class PermissionSeeder {

    /** {slug, name, group}. */
    static final String[][] PERMISSIONS = {
        {"ticket.view", "View tickets", "Tickets"},
        {"ticket.create", "Create tickets", "Tickets"},
        {"ticket.edit", "Edit ticket properties", "Tickets"},
        {"ticket.delete", "Delete tickets", "Tickets"},
        {"ticket.assign", "Assign tickets", "Tickets"},
        {"ticket.merge", "Merge tickets", "Tickets"},
        {"ticket.close", "Close tickets", "Tickets"},
        {"ticket.export", "Export tickets", "Tickets"},
        {"reply.create", "Reply to tickets", "Replies"},
        {"reply.create_internal", "Add internal notes", "Replies"},
        {"reply.edit", "Edit replies", "Replies"},
        {"reply.delete", "Delete replies", "Replies"},
        {"kb.view", "View knowledge base", "Knowledge Base"},
        {"kb.create", "Create articles", "Knowledge Base"},
        {"kb.edit", "Edit articles", "Knowledge Base"},
        {"kb.delete", "Delete articles", "Knowledge Base"},
        {"kb.publish", "Publish articles", "Knowledge Base"},
        {"department.view", "View departments", "Departments"},
        {"department.create", "Create departments", "Departments"},
        {"department.edit", "Edit departments", "Departments"},
        {"department.delete", "Delete departments", "Departments"},
        {"report.view", "View reports", "Reports"},
        {"report.export", "Export reports", "Reports"},
        {"sla.view", "View SLA policies", "SLA"},
        {"sla.manage", "Manage SLA policies", "SLA"},
        {"automation.view", "View automations", "Automations"},
        {"automation.manage", "Manage automations", "Automations"},
        {"escalation.view", "View escalation rules", "Escalation Rules"},
        {"escalation.manage", "Manage escalation rules", "Escalation Rules"},
        {"macro.view", "View macros", "Macros"},
        {"macro.create", "Create macros", "Macros"},
        {"macro.manage", "Manage macros", "Macros"},
        {"tag.view", "View tags", "Tags"},
        {"tag.manage", "Manage tags", "Tags"},
        {"custom_field.view", "View custom fields", "Custom Fields"},
        {"custom_field.manage", "Manage custom fields", "Custom Fields"},
        {"role.view", "View roles", "Roles"},
        {"role.manage", "Manage roles", "Roles"},
        {"user.view", "View users", "Users"},
        {"user.manage", "Manage users", "Users"},
        {"settings.view", "View settings", "Settings"},
        {"settings.manage", "Manage settings", "Settings"},
        {"webhook.view", "View webhooks", "Webhooks"},
        {"webhook.manage", "Manage webhooks", "Webhooks"},
        {"api_token.view", "View API tokens", "API Tokens"},
        {"api_token.manage", "Manage API tokens", "API Tokens"},
        {"audit.view", "View audit log", "Audit Log"},
        {"plugin.view", "View plugins", "Plugins"},
        {"plugin.manage", "Manage plugins", "Plugins"},
        {"custom_object.view", "View custom objects", "Custom Objects"},
        {"custom_object.manage", "Manage custom objects", "Custom Objects"},
        {"custom_object.data", "Manage custom object data", "Custom Objects"},
        {"newsletters.manage", "Manage newsletters", "Newsletters"},
        {"newsletters.send", "Send newsletters", "Newsletters"},
    };

    /** The system roles: {name, description, permission patterns}. {@code *} is every permission. */
    static final List<Object[]> ROLES = List.of(
            new Object[] {"admin", "Full access to all features and settings.", List.of("*")},
            new Object[] {"agent", "Standard agent with ticket handling and limited administrative access.",
                List.of("ticket.*", "reply.*", "kb.view", "report.view", "macro.view", "macro.create", "tag.view",
                        "custom_field.view", "audit.view")},
            new Object[] {"light_agent", "Limited agent with read-only ticket access and internal note capability.",
                List.of("ticket.view", "reply.create", "reply.create_internal", "kb.view", "macro.view",
                        "tag.view")});

    private static final Logger log = LoggerFactory.getLogger(PermissionSeeder.class);

    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final AgentProfileRepository agents;
    private final AccessFeatures features;

    public PermissionSeeder(PermissionRepository permissions, RoleRepository roles, AgentProfileRepository agents,
                            AccessFeatures features) {
        this.permissions = permissions;
        this.roles = roles;
        this.agents = agents;
        this.features = features;
    }

    /** Seeds at startup while roles and permissions is on, so a Hibernate-built schema has them too. */
    @EventListener(ApplicationReadyEvent.class)
    public void seedWhenEnforced() {
        if (!features.rolesPermissions()) {
            return;
        }
        try {
            seed();
        } catch (RuntimeException failed) {
            log.warn("Escalated could not seed its permissions and system roles: {}", failed.toString());
        }
    }

    /** Creates the missing permissions and system roles. Safe to call repeatedly. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void seed() {
        for (String[] definition : PERMISSIONS) {
            if (permissions.findByName(definition[0]).isEmpty()) {
                Permission permission = new Permission();
                permission.setName(definition[0]);
                permission.setDescription(definition[1]);
                permission.setCategory(definition[2]);
                permissions.save(permission);
            }
        }
        List<Permission> all = permissions.findAll();
        for (Object[] definition : ROLES) {
            String name = (String) definition[0];
            Role role = roles.findByName(name).orElseGet(() -> {
                Role created = new Role();
                created.setName(name);
                created.setDescription((String) definition[1]);
                created.setSystem(true);
                return created;
            });
            @SuppressWarnings("unchecked")
            List<String> patterns = (List<String>) definition[2];
            for (Permission permission : all) {
                if (matches(patterns, permission.getName())
                        && role.getPermissions().stream().noneMatch(held -> held.getId().equals(permission.getId()))) {
                    role.getPermissions().add(permission);
                }
            }
            roles.save(role);
        }
    }

    /**
     * Gives the user with {@code email} the role named {@code roleName}, replacing the one they had.
     * This port keeps one role per agent profile.
     */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public AgentProfile assignRole(String email, String roleName) {
        AgentProfile agent = agents.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("No agent profile for " + email));
        Role role = roles.findByName(roleName)
                .orElseThrow(() -> new EntityNotFoundException("No role named " + roleName));
        agent.setRole(role);
        return agents.save(agent);
    }

    static boolean matches(List<String> patterns, String slug) {
        for (String pattern : patterns) {
            if ("*".equals(pattern)) {
                return true;
            }
            if (pattern.endsWith(".*") && slug.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
            if (pattern.toLowerCase(Locale.ROOT).equals(slug)) {
                return true;
            }
        }
        return false;
    }

    /** The slugs this catalog seeds. */
    public static List<String> slugs() {
        List<String> slugs = new ArrayList<>();
        for (String[] definition : PERMISSIONS) {
            slugs.add(definition[0]);
        }
        return slugs;
    }
}
