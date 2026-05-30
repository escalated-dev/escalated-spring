-- Ticket subjects — host-app entities a ticket is *about* (Project, Customer, asset, …),
-- distinct from the requester and the subject line. subject_id is VARCHAR so integer,
-- UUID, ULID, or other string host keys all work.

CREATE TABLE escalated_ticket_subjects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    subject_type VARCHAR(255) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    role VARCHAR(255),
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_escalated_ticket_subjects_ticket FOREIGN KEY (ticket_id) REFERENCES escalated_tickets (id) ON DELETE CASCADE,
    CONSTRAINT uq_escalated_ticket_subjects_ticket_type_id UNIQUE (ticket_id, subject_type, subject_id)
);

CREATE INDEX idx_escalated_ticket_subjects_type_id ON escalated_ticket_subjects (subject_type, subject_id);
