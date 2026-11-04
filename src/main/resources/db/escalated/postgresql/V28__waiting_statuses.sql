-- PostgreSQL edition of db/escalated/mysql/V28__waiting_statuses.sql.
-- The two are one migration: change them together, never one alone.

-- escalated-laravel's two waiting statuses replace this port's single PENDING.
--
-- PENDING was set by hand (it was what the frontend's "Waiting on customer"
-- and "Waiting on agent" both stored) and meant the ticket was parked for a
-- reply, so a stored PENDING becomes WAITING_ON_CUSTOMER. A ticket with a
-- custom status of the pending category keeps it; its anchor becomes
-- WAITING_ON_CUSTOMER, the pending category's anchor now.
--
-- The pending system row becomes waiting_on_customer (same id, so custom
-- status links and history keep pointing at it), and waiting_on_agent gets a
-- system row in the open category. Both slugs were reserved for the built-in
-- statuses, so no custom status can hold them.

UPDATE escalated_tickets SET status = 'WAITING_ON_CUSTOMER' WHERE status = 'PENDING';

UPDATE escalated_ticket_statuses
   SET slug = 'waiting_on_customer', label = 'Waiting on Customer'
 WHERE slug = 'pending' AND is_system = TRUE;

INSERT INTO escalated_ticket_statuses (label, slug, category, color, position, is_default, is_system) VALUES
    ('Waiting on Agent', 'waiting_on_agent', 'open', '#f97316', 2, FALSE, TRUE);
