-- Pattern B: first-class Contact entity for guest requester dedupe.
-- Reference: escalated-dev/escalated
-- docs/superpowers/plans/2026-04-24-public-tickets-rollout-status.md
--
-- Spring is greenfield for public ticketing (no prior guest_* inline
-- columns), so we add the contact FK on tickets AND introduce the
-- contacts table in a single migration.

CREATE TABLE escalated_contacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    name VARCHAR(255),
    user_id BIGINT,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_contact_email UNIQUE (email)
);

CREATE INDEX idx_contact_user ON escalated_contacts(user_id);

ALTER TABLE escalated_tickets
    ADD COLUMN contact_id BIGINT NULL;

ALTER TABLE escalated_tickets
    ADD CONSTRAINT fk_ticket_contact
    FOREIGN KEY (contact_id) REFERENCES escalated_contacts(id)
    ON DELETE SET NULL;

CREATE INDEX idx_ticket_contact ON escalated_tickets(contact_id);
