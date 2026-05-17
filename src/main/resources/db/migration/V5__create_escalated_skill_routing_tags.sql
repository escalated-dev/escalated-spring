CREATE TABLE escalated_skill_routing_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_escalated_skill_routing_tags_skill_tag UNIQUE (skill_id, tag_id),
    CONSTRAINT fk_escalated_skill_routing_tags_skill FOREIGN KEY (skill_id) REFERENCES escalated_skills (id) ON DELETE CASCADE,
    CONSTRAINT fk_escalated_skill_routing_tags_tag FOREIGN KEY (tag_id) REFERENCES escalated_tags (id) ON DELETE CASCADE
);
