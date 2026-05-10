-- Surface admin / agent role flags directly on the agent profile, so the
-- admin users-management page can grant or revoke either role without
-- assuming the host application has its own boolean columns. Hosts using
-- a different role implementation (Spring Security GrantedAuthorities,
-- a custom user table, etc.) can override AdminUserController in their
-- own configuration.

ALTER TABLE escalated_agent_profiles
    ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE escalated_agent_profiles
    ADD COLUMN is_agent BOOLEAN NOT NULL DEFAULT TRUE;
