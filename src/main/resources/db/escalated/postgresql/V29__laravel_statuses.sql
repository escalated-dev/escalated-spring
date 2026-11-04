-- PostgreSQL edition of db/escalated/mysql/V29__laravel_statuses.sql.
-- The two are one migration: change them together, never one alone.

-- escalated-laravel's built-in escalated, reopened and live statuses.
--
-- Stored tickets keep their status: nothing was escalated, reopened or live
-- before. Each new status gets a system row in the open category, as the
-- others have.
--
-- Their slugs were not reserved until now, so a custom status may hold one.
-- It moves to <slug>_2 and keeps its id, so tickets keep it; an input naming
-- the slug means the built-in status from here on.

UPDATE escalated_ticket_statuses
   SET slug = slug || '_2'
 WHERE slug IN ('escalated', 'reopened', 'live') AND is_system = FALSE;

INSERT INTO escalated_ticket_statuses (label, slug, category, color, position, is_default, is_system) VALUES
    ('Escalated', 'escalated', 'open', '#ef4444', 3, FALSE, TRUE),
    ('Reopened', 'reopened', 'open', '#3b82f6', 4, FALSE, TRUE),
    ('Live', 'live', 'open', '#06b6d4', 5, FALSE, TRUE);
