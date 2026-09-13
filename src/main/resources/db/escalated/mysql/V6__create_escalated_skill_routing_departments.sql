CREATE TABLE escalated_skill_routing_departments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_escalated_skill_routing_departments_skill_dept UNIQUE (skill_id, department_id),
    CONSTRAINT fk_escalated_skill_routing_departments_skill FOREIGN KEY (skill_id) REFERENCES escalated_skills (id) ON DELETE CASCADE,
    CONSTRAINT fk_escalated_skill_routing_departments_department FOREIGN KEY (department_id) REFERENCES escalated_departments (id) ON DELETE CASCADE
);
