-- Retry scheduling column (dispatcher backoff) + newsletter permission slugs.

ALTER TABLE escalated_newsletter_deliveries
    ADD COLUMN next_attempt_at TIMESTAMP;

CREATE INDEX idx_nd_next_attempt ON escalated_newsletter_deliveries (status, next_attempt_at);

INSERT INTO escalated_permissions (name, description, category)
VALUES ('newsletters.manage', 'Create, edit, delete drafts and lists/templates; send test emails', 'newsletters');

INSERT INTO escalated_permissions (name, description, category)
VALUES ('newsletters.send', 'Schedule or send newsletters now', 'newsletters');

INSERT INTO escalated_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM escalated_roles r
CROSS JOIN escalated_permissions p
WHERE r.name = 'admin'
  AND p.name IN ('newsletters.manage', 'newsletters.send');
