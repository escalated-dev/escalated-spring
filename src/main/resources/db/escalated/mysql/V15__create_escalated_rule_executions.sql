-- Fire log for the sweeps: escalation rules, SLA warnings and SLA breaches.
-- One row per (rule, ticket) records when the rule last acted on the ticket.
-- A sweep skips a ticket that already has a row, so a rule fires once per
-- ticket; the row is removed once the ticket stops matching (re-arming the
-- rule), and an escalation rule's optional repeat_after_hours lets it fire
-- again after that many hours while the ticket still matches.

CREATE TABLE escalated_rule_executions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_type VARCHAR(32) NOT NULL,
    rule_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    fired_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_escalated_rule_executions_ticket FOREIGN KEY (ticket_id) REFERENCES escalated_tickets (id) ON DELETE CASCADE,
    CONSTRAINT uq_escalated_rule_executions_rule_ticket UNIQUE (rule_type, rule_id, ticket_id)
);

CREATE INDEX idx_escalated_rule_executions_ticket ON escalated_rule_executions (ticket_id);

ALTER TABLE escalated_escalation_rules ADD COLUMN repeat_after_hours INT NULL;
