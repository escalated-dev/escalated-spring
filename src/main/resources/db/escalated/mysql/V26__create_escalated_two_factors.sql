-- MySQL edition. Two-factor enrolments (ADR 2026-10-29-configurable-access-features): one per
-- agent profile, pending until confirmed. secret and recovery_codes are encrypted by the application;
-- last_used_step is the time step of the last accepted code, which is never accepted again.
CREATE TABLE escalated_two_factors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL UNIQUE,
    secret TEXT NOT NULL,
    recovery_codes TEXT,
    confirmed_at TIMESTAMP NULL,
    last_used_step BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_escalated_two_factors_agent FOREIGN KEY (agent_id)
        REFERENCES escalated_agent_profiles(id) ON DELETE CASCADE
);
