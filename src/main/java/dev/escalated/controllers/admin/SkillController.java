package dev.escalated.controllers.admin;

import dev.escalated.dtos.admin.CreateSkillDto;
import dev.escalated.dtos.admin.UpdateSkillDto;
import dev.escalated.models.Skill;
import dev.escalated.services.SkillService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Skills API backing {@code Escalated/Admin/Skills/*} in the shared
 * frontend. Wire shape follows {@code domain-model/skills-management.md}
 * (snake_case keys).
 */
@RestController
@RequestMapping("/escalated/api/admin/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> index() {
        return ResponseEntity.ok(skillService.listForAdmin());
    }

    @GetMapping("/new")
    public ResponseEntity<Map<String, Object>> createForm() {
        return ResponseEntity.ok(skillService.getFormContext());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> store(@Valid @RequestBody CreateSkillDto body) {
        try {
            Skill skill = skillService.create(body);
            LinkedHashMap<String, Object> res = new LinkedHashMap<>();
            res.put("skill", skillService.findForEdit(skill.getId()).get("skill"));
            res.put("message", "Skill created.");
            return ResponseEntity.status(201).body(res);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/edit")
    public ResponseEntity<Map<String, Object>> edit(@PathVariable long id) {
        try {
            return ResponseEntity.ok(skillService.findForEdit(id));
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable long id, @Valid @RequestBody UpdateSkillDto body) {
        try {
            skillService.update(id, body);
            LinkedHashMap<String, Object> res = new LinkedHashMap<>();
            res.put("skill", skillService.findForEdit(id).get("skill"));
            res.put("message", "Skill updated.");
            return ResponseEntity.ok(res);
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> destroy(@PathVariable long id) {
        try {
            skillService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
