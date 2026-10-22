-- MySQL edition of db/escalated/postgresql/V18__laravel_sla_policies_and_escalation_rules.sql.
-- The two are one migration: change them together, never one alone.

-- SLA policies and escalation rules take escalated-laravel's shape, which the
-- shared admin pages edit, and existing rows are converted so that they keep
-- doing what they did.
--
-- SLA policies: one priority with minute targets becomes per-priority hour
-- maps ({"high": 4}). A converted policy covers the one priority it had and is
-- marked default, so the selection (a default policy with a figure for the
-- ticket's priority) picks the same policy as before. A policy with a business
-- schedule becomes business_hours_only and keeps its schedule.
ALTER TABLE escalated_sla_policies ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE escalated_sla_policies ADD COLUMN first_response_hours TEXT NULL;
ALTER TABLE escalated_sla_policies ADD COLUMN resolution_hours TEXT NULL;
ALTER TABLE escalated_sla_policies ADD COLUMN business_hours_only BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE escalated_sla_policies SET
    is_default = TRUE,
    business_hours_only = (business_schedule_id IS NOT NULL),
    first_response_hours = CONCAT('{"', LOWER(priority), '":',
        CASE WHEN first_response_minutes % 60 = 0 THEN CAST(first_response_minutes DIV 60 AS CHAR)
             ELSE CAST(ROUND(first_response_minutes / 60, 4) AS CHAR) END,
        '}'),
    resolution_hours = CONCAT('{"', LOWER(priority), '":',
        CASE WHEN resolution_minutes % 60 = 0 THEN CAST(resolution_minutes DIV 60 AS CHAR)
             ELSE CAST(ROUND(resolution_minutes / 60, 4) AS CHAR) END,
        '}');

ALTER TABLE escalated_sla_policies MODIFY first_response_hours TEXT NOT NULL;
ALTER TABLE escalated_sla_policies MODIFY resolution_hours TEXT NOT NULL;
ALTER TABLE escalated_sla_policies DROP COLUMN priority;
ALTER TABLE escalated_sla_policies DROP COLUMN first_response_minutes;
ALTER TABLE escalated_sla_policies DROP COLUMN resolution_minutes;

-- Escalation rules: standalone, with a list of {field, value} conditions and a
-- list of {type, value} actions. A rule that hung off an SLA policy and acted
-- when the ticket's resolution deadline had passed becomes the conditions
-- sla_policy_id and sla_breached = resolution; its routing conditions (V12)
-- are kept whole as a workflow_conditions condition. Its single action becomes
-- the one-item action list; reassign is assign_to. The old ordering
-- (minutes_before_or_after) becomes the position.
ALTER TABLE escalated_escalation_rules ADD COLUMN description TEXT NULL;
ALTER TABLE escalated_escalation_rules ADD COLUMN category VARCHAR(255) NOT NULL DEFAULT 'Uncategorized';
ALTER TABLE escalated_escalation_rules ADD COLUMN actions TEXT NULL;
ALTER TABLE escalated_escalation_rules ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE escalated_escalation_rules SET
    conditions = CONCAT('[{"field":"sla_policy_id","value":"', CAST(sla_policy_id AS CHAR),
        '"},{"field":"sla_breached","value":"resolution"}',
        CASE WHEN conditions IS NULL OR TRIM(conditions) = '' THEN ''
             ELSE CONCAT(',{"field":"workflow_conditions","value":', conditions, '}') END,
        ']'),
    actions = CASE
        WHEN action_type = 'reassign' AND COALESCE(action_target, '') = '' THEN '[]'
        ELSE CONCAT('[{"type":"',
            CASE action_type WHEN 'reassign' THEN 'assign_to'
                 ELSE REPLACE(REPLACE(action_type, '\\', '\\\\'), '"', '\\"') END,
            '","value":"',
            REPLACE(REPLACE(REPLACE(REPLACE(
                CASE action_type WHEN 'notify' THEN COALESCE(notify_emails, '')
                     WHEN 'change_priority' THEN LOWER(COALESCE(action_target, ''))
                     ELSE COALESCE(action_target, '') END,
                '\\', '\\\\'), '"', '\\"'), CHAR(13), '\\r'), CHAR(10), '\\n'),
            '"}]')
        END;

UPDATE escalated_escalation_rules rules
JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY minutes_before_or_after, id) - 1 AS position
      FROM escalated_escalation_rules) ranked ON rules.id = ranked.id
SET rules.sort_order = ranked.position;

-- The policy's foreign key was created unnamed, so it is looked up by column.
SET @escalated_rule_policy_fk = (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'escalated_escalation_rules'
      AND COLUMN_NAME = 'sla_policy_id' AND REFERENCED_TABLE_NAME IS NOT NULL
    LIMIT 1);
SET @escalated_rule_policy_sql = IF(@escalated_rule_policy_fk IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE escalated_escalation_rules DROP FOREIGN KEY `', @escalated_rule_policy_fk, '`'));
PREPARE escalated_rule_policy_stmt FROM @escalated_rule_policy_sql;
EXECUTE escalated_rule_policy_stmt;
DEALLOCATE PREPARE escalated_rule_policy_stmt;

ALTER TABLE escalated_escalation_rules MODIFY conditions TEXT NOT NULL;
ALTER TABLE escalated_escalation_rules MODIFY actions TEXT NOT NULL;
ALTER TABLE escalated_escalation_rules DROP COLUMN sla_policy_id;
ALTER TABLE escalated_escalation_rules DROP COLUMN minutes_before_or_after;
ALTER TABLE escalated_escalation_rules DROP COLUMN action_type;
ALTER TABLE escalated_escalation_rules DROP COLUMN action_target;
ALTER TABLE escalated_escalation_rules DROP COLUMN notify_emails;
ALTER TABLE escalated_escalation_rules ALTER COLUMN trigger_type SET DEFAULT 'time_based';
