-- The agent panel's data (ADR 2026-11-05-agent-panel-data):
--   * tickets.last_customer_activity_at and escalated_ticket_reads, the per-agent unread
--     markers. Existing tickets get no activity, so none turn unread on upgrade;
--   * escalated_customer_presence, when each customer (by email) was last seen;
--   * agent_profiles.chat_status, the agent's own Online / Away / Offline status;
--   * escalated_notifications, the agents' in-app notifications.
-- The PostgreSQL edition is db/escalated/postgresql/V30__agent_panel_data.sql: change them together.

ALTER TABLE escalated_tickets ADD COLUMN last_customer_activity_at TIMESTAMP NULL;
CREATE INDEX idx_escalated_tickets_customer_activity ON escalated_tickets (last_customer_activity_at);

ALTER TABLE escalated_agent_profiles ADD COLUMN chat_status VARCHAR(20) NOT NULL DEFAULT 'offline';

CREATE TABLE escalated_ticket_reads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    last_read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_escalated_ticket_reads_ticket FOREIGN KEY (ticket_id) REFERENCES escalated_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_escalated_ticket_reads_agent FOREIGN KEY (agent_id) REFERENCES escalated_agent_profiles (id) ON DELETE CASCADE,
    CONSTRAINT uq_escalated_ticket_reads_agent_ticket UNIQUE (agent_id, ticket_id)
);

CREATE INDEX idx_escalated_ticket_reads_ticket ON escalated_ticket_reads (ticket_id);

CREATE TABLE escalated_customer_presence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(191) NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_escalated_customer_presence_email UNIQUE (email)
);

CREATE TABLE escalated_notifications (
    id VARCHAR(36) PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    type VARCHAR(64) NOT NULL,
    data TEXT NOT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_escalated_notifications_agent FOREIGN KEY (agent_id) REFERENCES escalated_agent_profiles (id) ON DELETE CASCADE
);

CREATE INDEX idx_escalated_notifications_agent ON escalated_notifications (agent_id, read_at);
