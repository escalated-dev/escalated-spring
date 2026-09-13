-- Newsletter system — admin broadcast feature (lists, members, templates,
-- newsletters, deliveries) + marketing_opt_out_at on contacts. Mirrors the
-- Laravel/NestJS schema and the JPA entities under models/newsletter.
--
-- The entities shipped without a Flyway migration; production (Flyway enabled,
-- Hibernate ddl-auto not create) therefore never created these tables. Tests
-- masked it by using ddl-auto=create-drop with Flyway disabled.

CREATE TABLE escalated_newsletter_lists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    kind VARCHAR(16) NOT NULL DEFAULT 'static',
    filter_json TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_nl_kind ON escalated_newsletter_lists (kind);
CREATE INDEX idx_nl_created_by ON escalated_newsletter_lists (created_by);

CREATE TABLE escalated_newsletter_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    theme VARCHAR(64) NOT NULL DEFAULT 'default',
    subject_template VARCHAR(998),
    body_markdown TEXT NOT NULL,
    merge_fields_schema TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_nlt_theme ON escalated_newsletter_templates (theme);
CREATE INDEX idx_nlt_created_by ON escalated_newsletter_templates (created_by);

CREATE TABLE escalated_newsletter_list_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    list_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by VARCHAR(255),
    CONSTRAINT uniq_nlm_list_contact UNIQUE (list_id, contact_id),
    CONSTRAINT fk_nlm_list FOREIGN KEY (list_id) REFERENCES escalated_newsletter_lists (id) ON DELETE CASCADE,
    CONSTRAINT fk_nlm_contact FOREIGN KEY (contact_id) REFERENCES escalated_contacts (id) ON DELETE CASCADE
);
CREATE INDEX idx_nlm_contact ON escalated_newsletter_list_members (contact_id);

CREATE TABLE escalated_newsletters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject VARCHAR(998) NOT NULL,
    from_email VARCHAR(320) NOT NULL,
    from_name VARCHAR(255),
    reply_to VARCHAR(320),
    target_list_id BIGINT NOT NULL,
    template_id BIGINT,
    theme VARCHAR(64),
    body_markdown TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'draft',
    scheduled_at TIMESTAMP,
    sent_at TIMESTAMP,
    created_by VARCHAR(255),
    sent_by VARCHAR(255),
    summary_total INT NOT NULL DEFAULT 0,
    summary_sent INT NOT NULL DEFAULT 0,
    summary_opened INT NOT NULL DEFAULT 0,
    summary_clicked INT NOT NULL DEFAULT 0,
    summary_bounced INT NOT NULL DEFAULT 0,
    summary_complained INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_n_target_list FOREIGN KEY (target_list_id) REFERENCES escalated_newsletter_lists (id),
    CONSTRAINT fk_n_template FOREIGN KEY (template_id) REFERENCES escalated_newsletter_templates (id) ON DELETE SET NULL
);
CREATE INDEX idx_n_status ON escalated_newsletters (status);
CREATE INDEX idx_n_scheduled_at ON escalated_newsletters (scheduled_at);
CREATE INDEX idx_n_status_sched ON escalated_newsletters (status, scheduled_at);
CREATE INDEX idx_n_created_by ON escalated_newsletters (created_by);

CREATE TABLE escalated_newsletter_deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    newsletter_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    email_at_send VARCHAR(320) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    tracking_token VARCHAR(40) NOT NULL,
    sent_at TIMESTAMP,
    opened_at TIMESTAMP,
    last_clicked_at TIMESTAMP,
    clicks_count INT NOT NULL DEFAULT 0,
    bounce_reason TEXT,
    failure_reason TEXT,
    attempt_count INT NOT NULL DEFAULT 0,
    claimed_at TIMESTAMP,
    is_test BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_nd_token UNIQUE (tracking_token),
    CONSTRAINT fk_nd_newsletter FOREIGN KEY (newsletter_id) REFERENCES escalated_newsletters (id) ON DELETE CASCADE,
    CONSTRAINT fk_nd_contact FOREIGN KEY (contact_id) REFERENCES escalated_contacts (id) ON DELETE CASCADE
);
CREATE INDEX idx_nd_nl_status ON escalated_newsletter_deliveries (newsletter_id, status);
CREATE INDEX idx_nd_contact ON escalated_newsletter_deliveries (contact_id);
CREATE INDEX idx_nd_status_claimed ON escalated_newsletter_deliveries (status, claimed_at);

ALTER TABLE escalated_contacts ADD COLUMN marketing_opt_out_at TIMESTAMP;
CREATE INDEX idx_contact_opt_out ON escalated_contacts (marketing_opt_out_at);
