-- Replace legacy M2M (composite key, no proficiency) with skills-management junction.
DROP TABLE IF EXISTS escalated_agent_skills;

-- Contract: slug on escalated_skills (backfill deterministically from id).
ALTER TABLE escalated_skills
    ADD COLUMN slug VARCHAR(100) NULL;

UPDATE escalated_skills
SET slug = CONCAT('skill-', id)
WHERE slug IS NULL;

ALTER TABLE escalated_skills
    MODIFY COLUMN slug VARCHAR(100) NOT NULL;

ALTER TABLE escalated_skills
    ADD CONSTRAINT uq_escalated_skills_slug UNIQUE (slug);

CREATE TABLE escalated_agent_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    proficiency SMALLINT NOT NULL DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_escalated_agent_skills_proficiency CHECK (proficiency >= 1 AND proficiency <= 5),
    CONSTRAINT uq_escalated_agent_skills_user_skill UNIQUE (user_id, skill_id),
    CONSTRAINT fk_escalated_agent_skills_skill FOREIGN KEY (skill_id) REFERENCES escalated_skills (id) ON DELETE CASCADE,
    CONSTRAINT fk_escalated_agent_skills_user FOREIGN KEY (user_id) REFERENCES escalated_agent_profiles (id) ON DELETE CASCADE
);
