-- Escalated Helpdesk Schema

CREATE TABLE escalated_departments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    category VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES escalated_roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES escalated_permissions(id) ON DELETE CASCADE
);

CREATE TABLE escalated_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_agent_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    avatar VARCHAR(255),
    phone VARCHAR(255),
    signature TEXT,
    two_factor_secret VARCHAR(255),
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    department_id BIGINT,
    role_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (department_id) REFERENCES escalated_departments(id) ON DELETE SET NULL,
    FOREIGN KEY (role_id) REFERENCES escalated_roles(id) ON DELETE SET NULL
);

CREATE TABLE escalated_agent_skills (
    agent_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (agent_id, skill_id),
    FOREIGN KEY (agent_id) REFERENCES escalated_agent_profiles(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id) REFERENCES escalated_skills(id) ON DELETE CASCADE
);

CREATE TABLE escalated_agent_capacities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL UNIQUE,
    max_tickets INT NOT NULL DEFAULT 20,
    current_tickets INT NOT NULL DEFAULT 0,
    weight INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (agent_id) REFERENCES escalated_agent_profiles(id) ON DELETE CASCADE
);

CREATE TABLE escalated_business_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    timezone VARCHAR(255) NOT NULL DEFAULT 'UTC',
    monday_start VARCHAR(5) DEFAULT '09:00',
    monday_end VARCHAR(5) DEFAULT '17:00',
    tuesday_start VARCHAR(5) DEFAULT '09:00',
    tuesday_end VARCHAR(5) DEFAULT '17:00',
    wednesday_start VARCHAR(5) DEFAULT '09:00',
    wednesday_end VARCHAR(5) DEFAULT '17:00',
    thursday_start VARCHAR(5) DEFAULT '09:00',
    thursday_end VARCHAR(5) DEFAULT '17:00',
    friday_start VARCHAR(5) DEFAULT '09:00',
    friday_end VARCHAR(5) DEFAULT '17:00',
    saturday_start VARCHAR(5),
    saturday_end VARCHAR(5),
    sunday_start VARCHAR(5),
    sunday_end VARCHAR(5),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_holidays (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_schedule_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (business_schedule_id) REFERENCES escalated_business_schedules(id) ON DELETE CASCADE
);

CREATE TABLE escalated_sla_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    first_response_minutes INT NOT NULL,
    resolution_minutes INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    business_schedule_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (business_schedule_id) REFERENCES escalated_business_schedules(id) ON DELETE SET NULL
);

CREATE TABLE escalated_escalation_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sla_policy_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    trigger_type VARCHAR(255) NOT NULL DEFAULT 'sla_breach',
    minutes_before_or_after INT NOT NULL DEFAULT 0,
    action_type VARCHAR(255) NOT NULL DEFAULT 'reassign',
    action_target VARCHAR(255),
    notify_emails TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sla_policy_id) REFERENCES escalated_sla_policies(id) ON DELETE CASCADE
);

CREATE TABLE escalated_tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    ticket_number VARCHAR(255) NOT NULL UNIQUE,
    requester_name VARCHAR(255) NOT NULL,
    requester_email VARCHAR(255) NOT NULL,
    assigned_agent_id BIGINT,
    department_id BIGINT,
    sla_policy_id BIGINT,
    sla_due_at TIMESTAMP,
    sla_first_response_due_at TIMESTAMP,
    first_responded_at TIMESTAMP,
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,
    snoozed_until TIMESTAMP,
    merged_into_ticket_id BIGINT,
    email_message_id VARCHAR(255),
    guest_access_token VARCHAR(255),
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (assigned_agent_id) REFERENCES escalated_agent_profiles(id) ON DELETE SET NULL,
    FOREIGN KEY (department_id) REFERENCES escalated_departments(id) ON DELETE SET NULL,
    FOREIGN KEY (sla_policy_id) REFERENCES escalated_sla_policies(id) ON DELETE SET NULL
);

CREATE TABLE escalated_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    color VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_ticket_tags (
    ticket_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (ticket_id, tag_id),
    FOREIGN KEY (ticket_id) REFERENCES escalated_tickets(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES escalated_tags(id) ON DELETE CASCADE
);

CREATE TABLE escalated_replies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    body TEXT NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    author_email VARCHAR(255) NOT NULL,
    author_type VARCHAR(50) NOT NULL DEFAULT 'agent',
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,
    email_message_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES escalated_tickets(id) ON DELETE CASCADE
);

CREATE TABLE escalated_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT,
    reply_id BIGINT,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    file_size BIGINT,
    mime_type VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES escalated_tickets(id) ON DELETE CASCADE,
    FOREIGN KEY (reply_id) REFERENCES escalated_replies(id) ON DELETE CASCADE
);

CREATE TABLE escalated_ticket_activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    description TEXT,
    actor_name VARCHAR(255),
    actor_email VARCHAR(255),
    old_value VARCHAR(255),
    new_value VARCHAR(255),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES escalated_tickets(id) ON DELETE CASCADE
);

CREATE TABLE escalated_ticket_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_ticket_id BIGINT NOT NULL,
    target_ticket_id BIGINT NOT NULL,
    link_type VARCHAR(255) NOT NULL DEFAULT 'related',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_ticket_id) REFERENCES escalated_tickets(id) ON DELETE CASCADE,
    FOREIGN KEY (target_ticket_id) REFERENCES escalated_tickets(id) ON DELETE CASCADE
);

CREATE TABLE escalated_satisfaction_ratings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    rating INT NOT NULL DEFAULT 0,
    comment TEXT,
    rater_email VARCHAR(255) NOT NULL,
    rater_name VARCHAR(255),
    access_token VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES escalated_tickets(id) ON DELETE CASCADE
);

CREATE TABLE escalated_canned_responses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    shortcut VARCHAR(255),
    category VARCHAR(255),
    is_shared BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_agent_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_macros (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    actions TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_shared BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_agent_id BIGINT,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_side_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    subject VARCHAR(255) NOT NULL,
    participant_emails TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'open',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES escalated_tickets(id) ON DELETE CASCADE
);

CREATE TABLE escalated_side_conversation_replies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    side_conversation_id BIGINT NOT NULL,
    body TEXT NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    author_email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (side_conversation_id) REFERENCES escalated_side_conversations(id) ON DELETE CASCADE
);

CREATE TABLE escalated_api_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    abilities TEXT DEFAULT '*',
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    agent_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (agent_id) REFERENCES escalated_agent_profiles(id) ON DELETE CASCADE
);

CREATE TABLE escalated_webhooks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url VARCHAR(255) NOT NULL,
    secret VARCHAR(255),
    events TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    failure_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_webhook_deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    webhook_id BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT,
    response_status INT,
    response_body TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    attempt_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (webhook_id) REFERENCES escalated_webhooks(id) ON DELETE CASCADE
);

CREATE TABLE escalated_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(255) NOT NULL,
    entity_type VARCHAR(255) NOT NULL,
    entity_id BIGINT,
    actor_name VARCHAR(255),
    actor_email VARCHAR(255),
    actor_ip VARCHAR(255),
    old_values TEXT,
    new_values TEXT,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_custom_fields (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    field_key VARCHAR(255) NOT NULL UNIQUE,
    field_type VARCHAR(255) NOT NULL DEFAULT 'text',
    description TEXT,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    default_value VARCHAR(255),
    options TEXT,
    sort_order INT DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_custom_field_values (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    custom_field_id BIGINT NOT NULL,
    field_value TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES escalated_tickets(id) ON DELETE CASCADE,
    FOREIGN KEY (custom_field_id) REFERENCES escalated_custom_fields(id) ON DELETE CASCADE
);

CREATE TABLE escalated_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(255) NOT NULL UNIQUE,
    setting_value TEXT,
    setting_group VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escalated_saved_views (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    filters TEXT NOT NULL,
    sort_by VARCHAR(255),
    sort_direction VARCHAR(10) DEFAULT 'desc',
    columns TEXT,
    is_shared BOOLEAN NOT NULL DEFAULT FALSE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    agent_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (agent_id) REFERENCES escalated_agent_profiles(id) ON DELETE CASCADE
);

CREATE TABLE escalated_kb_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    slug VARCHAR(255) NOT NULL UNIQUE,
    sort_order INT DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    parent_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES escalated_kb_categories(id) ON DELETE SET NULL
);

CREATE TABLE escalated_kb_articles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    content TEXT NOT NULL,
    excerpt TEXT,
    category_id BIGINT,
    author_name VARCHAR(255),
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP,
    view_count INT NOT NULL DEFAULT 0,
    helpful_count INT NOT NULL DEFAULT 0,
    not_helpful_count INT NOT NULL DEFAULT 0,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES escalated_kb_categories(id) ON DELETE SET NULL
);

-- Seed default roles and permissions
INSERT INTO escalated_roles (name, description, is_system) VALUES ('admin', 'Administrator with full access', TRUE);
INSERT INTO escalated_roles (name, description, is_system) VALUES ('agent', 'Support agent', TRUE);
INSERT INTO escalated_roles (name, description, is_system) VALUES ('supervisor', 'Team supervisor', TRUE);

INSERT INTO escalated_permissions (name, description, category) VALUES ('tickets.view', 'View tickets', 'tickets');
INSERT INTO escalated_permissions (name, description, category) VALUES ('tickets.create', 'Create tickets', 'tickets');
INSERT INTO escalated_permissions (name, description, category) VALUES ('tickets.update', 'Update tickets', 'tickets');
INSERT INTO escalated_permissions (name, description, category) VALUES ('tickets.delete', 'Delete tickets', 'tickets');
INSERT INTO escalated_permissions (name, description, category) VALUES ('tickets.assign', 'Assign tickets', 'tickets');
INSERT INTO escalated_permissions (name, description, category) VALUES ('tickets.merge', 'Merge tickets', 'tickets');
INSERT INTO escalated_permissions (name, description, category) VALUES ('tickets.split', 'Split tickets', 'tickets');
INSERT INTO escalated_permissions (name, description, category) VALUES ('agents.manage', 'Manage agents', 'agents');
INSERT INTO escalated_permissions (name, description, category) VALUES ('departments.manage', 'Manage departments', 'departments');
INSERT INTO escalated_permissions (name, description, category) VALUES ('roles.manage', 'Manage roles', 'roles');
INSERT INTO escalated_permissions (name, description, category) VALUES ('webhooks.manage', 'Manage webhooks', 'webhooks');
INSERT INTO escalated_permissions (name, description, category) VALUES ('settings.manage', 'Manage settings', 'settings');
INSERT INTO escalated_permissions (name, description, category) VALUES ('audit.view', 'View audit logs', 'audit');
INSERT INTO escalated_permissions (name, description, category) VALUES ('kb.manage', 'Manage knowledge base', 'knowledge_base');
INSERT INTO escalated_permissions (name, description, category) VALUES ('import.execute', 'Execute imports', 'import');
