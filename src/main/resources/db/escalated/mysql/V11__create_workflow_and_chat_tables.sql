-- MySQL edition of db/escalated/postgresql/V11__create_workflow_and_chat_tables.sql.
-- The two are one migration: change them together, never one alone.
--
-- Tables and columns the entities have always mapped that no migration ever
-- created. The suite builds its schema with Hibernate's create-drop, so nothing
-- noticed; a host running the migrations under ddl-auto=validate could not
-- start. MigrationsOnTheHostsDatabaseTest now validates every entity against
-- the migrated schema on PostgreSQL and MySQL.

-- Ticket.channel: "email", "chat", "widget", ...
ALTER TABLE escalated_tickets
    ADD COLUMN channel VARCHAR(30) NOT NULL DEFAULT 'email';

-- NewsletterListMember extends BaseEntity, which maps both timestamps.
ALTER TABLE escalated_newsletter_list_members
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE escalated_newsletter_list_members
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE escalated_workflows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    trigger_event VARCHAR(255) NOT NULL,
    conditions JSON,
    actions JSON,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    position INT NOT NULL DEFAULT 0,
    stop_on_match BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_workflow_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    trigger_event VARCHAR(255) NOT NULL,
    conditions_matched BOOLEAN NOT NULL DEFAULT TRUE,
    actions_executed JSON,
    error_message TEXT,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_escalated_workflow_logs_workflow FOREIGN KEY (workflow_id) REFERENCES escalated_workflows (id) ON DELETE CASCADE,
    CONSTRAINT fk_escalated_workflow_logs_ticket FOREIGN KEY (ticket_id) REFERENCES escalated_tickets (id) ON DELETE CASCADE
);

CREATE TABLE escalated_deferred_workflow_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    remaining_actions JSON NOT NULL,
    run_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_deferred_status_runat ON escalated_deferred_workflow_jobs (status, run_at);

CREATE TABLE escalated_chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    visitor_name VARCHAR(255) NOT NULL DEFAULT 'Visitor',
    visitor_email VARCHAR(255),
    agent_id BIGINT,
    department_id BIGINT,
    status VARCHAR(30) NOT NULL DEFAULT 'waiting',
    accepted_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_escalated_chat_sessions_ticket FOREIGN KEY (ticket_id) REFERENCES escalated_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_escalated_chat_sessions_department FOREIGN KEY (department_id) REFERENCES escalated_departments (id) ON DELETE SET NULL
);

CREATE TABLE escalated_chat_routing_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    department_id BIGINT,
    agent_id BIGINT,
    conditions TEXT,
    priority INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_escalated_chat_routing_rules_department FOREIGN KEY (department_id) REFERENCES escalated_departments (id) ON DELETE SET NULL
);

-- AgentSkill.proficiency is an Integer; V4 made the column SMALLINT. Widening
-- loses nothing, and the 1-5 check constraint still holds.
ALTER TABLE escalated_agent_skills
    MODIFY COLUMN proficiency INT NOT NULL DEFAULT 3;

-- NewsletterDelivery.attemptCount is a short; V8 made the column INT.
ALTER TABLE escalated_newsletter_deliveries
    MODIFY COLUMN attempt_count SMALLINT NOT NULL DEFAULT 0;
