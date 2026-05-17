package dev.escalated.dtos.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class AgentSkillEntryDto {

    @NotNull
    @JsonProperty("user_id")
    private Long userId;

    private Integer proficiency;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getProficiency() {
        return proficiency == null ? Integer.valueOf(3) : proficiency;
    }

    public void setProficiency(Integer proficiency) {
        this.proficiency = proficiency;
    }
}
