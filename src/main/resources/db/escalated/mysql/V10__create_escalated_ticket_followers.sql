-- Ticket followers — host users who follow a ticket and are a notification
-- target alongside the assignee and requester. user_id is VARCHAR so integer,
-- UUID, ULID, or other string host keys all work. See issue #74.

CREATE TABLE escalated_ticket_followers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_escalated_ticket_followers_ticket FOREIGN KEY (ticket_id) REFERENCES escalated_tickets (id) ON DELETE CASCADE,
    CONSTRAINT uq_escalated_ticket_followers_ticket_user UNIQUE (ticket_id, user_id)
);

CREATE INDEX idx_escalated_ticket_followers_user ON escalated_ticket_followers (user_id);
