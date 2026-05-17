package dev.escalated.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "escalated_skills")
public class Skill extends BaseEntity {

    @NotBlank
    @Column(nullable = false, unique = true, length = 255)
    private String name;

    @NotBlank
    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AgentSkill> agentSkills = new ArrayList<>();

    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillRoutingTag> routingTags = new ArrayList<>();

    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillRoutingDepartment> routingDepartments = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public List<AgentSkill> getAgentSkills() {
        return agentSkills;
    }

    public void setAgentSkills(List<AgentSkill> agentSkills) {
        this.agentSkills = agentSkills;
    }

    public List<SkillRoutingTag> getRoutingTags() {
        return routingTags;
    }

    public void setRoutingTags(List<SkillRoutingTag> routingTags) {
        this.routingTags = routingTags;
    }

    public List<SkillRoutingDepartment> getRoutingDepartments() {
        return routingDepartments;
    }

    public void setRoutingDepartments(List<SkillRoutingDepartment> routingDepartments) {
        this.routingDepartments = routingDepartments;
    }
}
