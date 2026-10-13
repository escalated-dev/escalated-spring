-- Opt-in ticket access: preserve existing rows and never rewrite released migrations.
ALTER TABLE escalated_tickets ADD COLUMN account_type VARCHAR(191) COLLATE "C" NULL;
ALTER TABLE escalated_tickets ADD COLUMN account_id VARCHAR(191) COLLATE "C" NULL;
ALTER TABLE escalated_tickets ADD COLUMN ticket_type VARCHAR(100) NULL;
CREATE INDEX escalated_tickets_account_idx ON escalated_tickets (account_type, account_id);
ALTER TABLE escalated_sla_policies ADD COLUMN department_id BIGINT NULL;
ALTER TABLE escalated_sla_policies ADD COLUMN ticket_type VARCHAR(100) NULL;
CREATE INDEX escalated_sla_target_idx ON escalated_sla_policies (department_id, ticket_type);
ALTER TABLE escalated_escalation_rules ADD COLUMN conditions TEXT NULL;
CREATE TABLE escalated_department_agent (
    id BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT escalated_department_agent_unique UNIQUE (department_id, agent_id),
    CONSTRAINT escalated_da_department_fk FOREIGN KEY (department_id) REFERENCES escalated_departments(id) ON DELETE CASCADE,
    CONSTRAINT escalated_da_agent_fk FOREIGN KEY (agent_id) REFERENCES escalated_agent_profiles(id) ON DELETE CASCADE
);
CREATE INDEX escalated_da_agent_idx ON escalated_department_agent (agent_id);
