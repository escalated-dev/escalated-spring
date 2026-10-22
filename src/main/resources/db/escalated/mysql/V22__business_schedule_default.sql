-- A default business schedule: the one a business-hours SLA policy without its own follows.
-- At most one row is the default; the admin page clears the others when it sets one.
ALTER TABLE escalated_business_schedules ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;
