-- Host parameters a widget ticket or chat was sent with, as JSON text.
ALTER TABLE escalated_tickets ADD COLUMN metadata TEXT NULL;
