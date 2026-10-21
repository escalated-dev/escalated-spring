-- Verified guest access (escalated-laravel docs/guest-access.md).
--
-- escalated_guest_verifications holds the emailed 8-digit codes. Neither the
-- code nor the address is stored: both are HMAC digests. A code is bound to the
-- address and a purpose (ticket, chat, lookup), lasts ten minutes, allows five
-- guesses and is spent once. Rows also count towards the hourly code budgets.
--
-- A ticket's guest grant is an expiring, encrypted bearer token. The ticket
-- keeps only a SHA-256 of the grant nonce and its expiry, so issuing a new
-- grant revokes the earlier ones.

CREATE TABLE escalated_guest_verifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    email_digest VARCHAR(64) NOT NULL,
    client_digest VARCHAR(64) NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    code_digest VARCHAR(64) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_escalated_guest_verifications_public_id UNIQUE (public_id)
);

CREATE INDEX idx_escalated_guest_verifications_email ON escalated_guest_verifications (email_digest, created_at);

ALTER TABLE escalated_tickets ADD COLUMN guest_access_hash VARCHAR(64) NULL;
ALTER TABLE escalated_tickets ADD COLUMN guest_access_expires_at TIMESTAMP NULL;
