-- MySQL edition. Roles and permissions (ADR 2026-10-29-configurable-access-features):
-- escalated-laravel's permission set and its system roles admin, agent and light_agent, as its
-- PermissionSeeder (dev.escalated.services.PermissionSeeder seeds the same on a Hibernate-built schema).
-- Data only and idempotent: missing rows are added, nothing is removed. Earlier permission names
-- (tickets.view, ...) stay, but grant nothing once ESCALATED_ROLES_PERMISSIONS is on.

INSERT IGNORE INTO escalated_permissions (name, description, category) VALUES
    ('ticket.view', 'View tickets', 'Tickets'),
    ('ticket.create', 'Create tickets', 'Tickets'),
    ('ticket.edit', 'Edit ticket properties', 'Tickets'),
    ('ticket.delete', 'Delete tickets', 'Tickets'),
    ('ticket.assign', 'Assign tickets', 'Tickets'),
    ('ticket.merge', 'Merge tickets', 'Tickets'),
    ('ticket.close', 'Close tickets', 'Tickets'),
    ('ticket.export', 'Export tickets', 'Tickets'),
    ('reply.create', 'Reply to tickets', 'Replies'),
    ('reply.create_internal', 'Add internal notes', 'Replies'),
    ('reply.edit', 'Edit replies', 'Replies'),
    ('reply.delete', 'Delete replies', 'Replies'),
    ('kb.view', 'View knowledge base', 'Knowledge Base'),
    ('kb.create', 'Create articles', 'Knowledge Base'),
    ('kb.edit', 'Edit articles', 'Knowledge Base'),
    ('kb.delete', 'Delete articles', 'Knowledge Base'),
    ('kb.publish', 'Publish articles', 'Knowledge Base'),
    ('department.view', 'View departments', 'Departments'),
    ('department.create', 'Create departments', 'Departments'),
    ('department.edit', 'Edit departments', 'Departments'),
    ('department.delete', 'Delete departments', 'Departments'),
    ('report.view', 'View reports', 'Reports'),
    ('report.export', 'Export reports', 'Reports'),
    ('sla.view', 'View SLA policies', 'SLA'),
    ('sla.manage', 'Manage SLA policies', 'SLA'),
    ('automation.view', 'View automations', 'Automations'),
    ('automation.manage', 'Manage automations', 'Automations'),
    ('escalation.view', 'View escalation rules', 'Escalation Rules'),
    ('escalation.manage', 'Manage escalation rules', 'Escalation Rules'),
    ('macro.view', 'View macros', 'Macros'),
    ('macro.create', 'Create macros', 'Macros'),
    ('macro.manage', 'Manage macros', 'Macros'),
    ('tag.view', 'View tags', 'Tags'),
    ('tag.manage', 'Manage tags', 'Tags'),
    ('custom_field.view', 'View custom fields', 'Custom Fields'),
    ('custom_field.manage', 'Manage custom fields', 'Custom Fields'),
    ('role.view', 'View roles', 'Roles'),
    ('role.manage', 'Manage roles', 'Roles'),
    ('user.view', 'View users', 'Users'),
    ('user.manage', 'Manage users', 'Users'),
    ('settings.view', 'View settings', 'Settings'),
    ('settings.manage', 'Manage settings', 'Settings'),
    ('webhook.view', 'View webhooks', 'Webhooks'),
    ('webhook.manage', 'Manage webhooks', 'Webhooks'),
    ('api_token.view', 'View API tokens', 'API Tokens'),
    ('api_token.manage', 'Manage API tokens', 'API Tokens'),
    ('audit.view', 'View audit log', 'Audit Log'),
    ('plugin.view', 'View plugins', 'Plugins'),
    ('plugin.manage', 'Manage plugins', 'Plugins'),
    ('custom_object.view', 'View custom objects', 'Custom Objects'),
    ('custom_object.manage', 'Manage custom objects', 'Custom Objects'),
    ('custom_object.data', 'Manage custom object data', 'Custom Objects'),
    ('newsletters.manage', 'Manage newsletters', 'Newsletters'),
    ('newsletters.send', 'Send newsletters', 'Newsletters');

INSERT IGNORE INTO escalated_roles (name, description, is_system) VALUES
    ('admin', 'Full access to all features and settings.', TRUE),
    ('agent', 'Standard agent with ticket handling and limited administrative access.', TRUE),
    ('light_agent', 'Limited agent with read-only ticket access and internal note capability.', TRUE);

INSERT IGNORE INTO escalated_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM escalated_roles r
CROSS JOIN escalated_permissions p
WHERE r.name = 'admin';

INSERT IGNORE INTO escalated_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM escalated_roles r
CROSS JOIN escalated_permissions p
WHERE r.name = 'agent'
  AND p.name IN ('ticket.view', 'ticket.create', 'ticket.edit', 'ticket.delete', 'ticket.assign', 'ticket.merge', 'ticket.close', 'ticket.export', 'reply.create', 'reply.create_internal', 'reply.edit', 'reply.delete', 'kb.view', 'report.view', 'macro.view', 'macro.create', 'tag.view', 'custom_field.view', 'audit.view');

INSERT IGNORE INTO escalated_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM escalated_roles r
CROSS JOIN escalated_permissions p
WHERE r.name = 'light_agent'
  AND p.name IN ('ticket.view', 'reply.create', 'reply.create_internal', 'kb.view', 'macro.view', 'tag.view');
