-- Admin-defined, time-based automations (Laravel's AutomationRunner): every
-- sweep, each active automation acts once on each open ticket matching its
-- hours_since_* and field conditions, and again only after the ticket stops
-- matching or after repeat_after_hours. Fire records live in
-- escalated_rule_executions with rule_type 'automation'.

CREATE TABLE escalated_automations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    conditions JSON NOT NULL,
    actions JSON NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    position INT NOT NULL DEFAULT 0,
    repeat_after_hours INT NULL,
    last_run_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_escalated_automations_active ON escalated_automations (is_active, position);
