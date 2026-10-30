-- MySQL edition. SSO message IDs (ADR 2026-10-29-configurable-access-features): issued SAML
-- AuthnRequest IDs, accepted assertion IDs and JWT jti values, kept until expires_at (Unix seconds),
-- so a response answers only a request this install made and nothing is accepted twice.
-- message_id is 191 characters so the unique key fits utf8mb4's index limit.
CREATE TABLE escalated_sso_message_ids (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kind VARCHAR(32) NOT NULL,
    message_id VARCHAR(191) NOT NULL,
    expires_at BIGINT NOT NULL,
    CONSTRAINT uq_escalated_sso_message_ids UNIQUE (kind, message_id)
);

CREATE INDEX idx_escalated_sso_message_ids_expires ON escalated_sso_message_ids (expires_at);
