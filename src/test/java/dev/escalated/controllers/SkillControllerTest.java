package dev.escalated.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.escalated.controllers.admin.SkillController;
import dev.escalated.models.Skill;
import dev.escalated.security.ApiTokenAuthenticationFilter;
import dev.escalated.services.SkillService;
import jakarta.persistence.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SkillController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiTokenAuthenticationFilter apiTokenFilter;

    @MockitoBean
    private SkillService skillService;

    @Test
    void index_returnsSkillsArray() throws Exception {
        when(skillService.listForAdmin()).thenReturn(Map.of("skills", List.of()));

        mockMvc.perform(get("/escalated/api/admin/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills").isArray());
    }

    @Test
    void createForm_returnsContext() throws Exception {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("skill", null);
        ctx.put("available_agents", List.of());
        ctx.put("available_tags", List.of());
        ctx.put("available_departments", List.of());
        when(skillService.getFormContext()).thenReturn(ctx);

        mockMvc.perform(get("/escalated/api/admin/skills/new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available_agents").isArray())
                .andExpect(jsonPath("$.available_tags").isArray())
                .andExpect(jsonPath("$.available_departments").isArray());
    }

    @Test
    void edit_returnsSkillPayload() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skill", Map.of("id", 1L, "name", "Alpha"));
        body.put("available_agents", List.of());
        body.put("available_tags", List.of());
        body.put("available_departments", List.of());
        when(skillService.findForEdit(1L)).thenReturn(body);

        mockMvc.perform(get("/escalated/api/admin/skills/1/edit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skill.name").value("Alpha"));
    }

    @Test
    void store_returns201OnSuccess() throws Exception {
        Skill created = new Skill();
        created.setId(5L);
        created.setName("Net");
        created.setSlug("net");
        when(skillService.create(any())).thenReturn(created);
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("skill", Map.of("id", 5, "name", "Net"));
        when(skillService.findForEdit(5L)).thenReturn(edit);

        mockMvc.perform(post("/escalated/api/admin/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Net\",\"routing_tag_ids\":[],"
                                + "\"routing_department_ids\":[],\"agents\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skill.id").value(5));
    }

    @Test
    void store_returns400WhenInvalid() throws Exception {
        when(skillService.create(any())).thenThrow(new IllegalArgumentException("bad"));

        mockMvc.perform(post("/escalated/api/admin/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Net\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad"));
    }

    @Test
    void edit_returns404WhenMissing() throws Exception {
        when(skillService.findForEdit(1L)).thenThrow(new EntityNotFoundException("missing"));

        mockMvc.perform(get("/escalated/api/admin/skills/1/edit")).andExpect(status().isNotFound());
    }

    @Test
    void update_returns200() throws Exception {
        Map<String, Object> skillMap = new LinkedHashMap<>();
        skillMap.put("id", 1L);
        when(skillService.findForEdit(1L)).thenReturn(Map.of("skill", skillMap));

        mockMvc.perform(put("/escalated/api/admin/skills/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"Net\",\"routing_tag_ids\":[],\"routing_department_ids\":[],\"agents\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Skill updated."));
    }

    @Test
    void destroy_returns204() throws Exception {
        mockMvc.perform(delete("/escalated/api/admin/skills/2")).andExpect(status().isNoContent());
    }
}
