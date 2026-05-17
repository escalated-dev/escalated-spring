package dev.escalated.dtos.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class CreateSkillDto {

    @NotBlank
    @Size(max = 100)
    private String name;

    private String description;

    @JsonProperty("routing_tag_ids")
    private List<Long> routingTagIds = new ArrayList<>();

    @JsonProperty("routing_department_ids")
    private List<Long> routingDepartmentIds = new ArrayList<>();

    @Valid
    private List<AgentSkillEntryDto> agents = new ArrayList<>();

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

    public List<Long> getRoutingTagIds() {
        return routingTagIds;
    }

    public void setRoutingTagIds(List<Long> routingTagIds) {
        this.routingTagIds = routingTagIds != null ? routingTagIds : new ArrayList<>();
    }

    public List<Long> getRoutingDepartmentIds() {
        return routingDepartmentIds;
    }

    public void setRoutingDepartmentIds(List<Long> routingDepartmentIds) {
        this.routingDepartmentIds = routingDepartmentIds != null ? routingDepartmentIds : new ArrayList<>();
    }

    public List<AgentSkillEntryDto> getAgents() {
        return agents;
    }

    public void setAgents(List<AgentSkillEntryDto> agents) {
        this.agents = agents != null ? agents : new ArrayList<>();
    }
}
