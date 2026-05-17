package dev.escalated.services;

import dev.escalated.dtos.admin.AgentSkillEntryDto;
import dev.escalated.dtos.admin.CreateSkillDto;
import dev.escalated.dtos.admin.UpdateSkillDto;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.AgentSkill;
import dev.escalated.models.Department;
import dev.escalated.models.Skill;
import dev.escalated.models.SkillRoutingDepartment;
import dev.escalated.models.SkillRoutingTag;
import dev.escalated.models.Tag;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AgentSkillRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.SkillRepository;
import dev.escalated.repositories.SkillRoutingDepartmentRepository;
import dev.escalated.repositories.SkillRoutingTagRepository;
import dev.escalated.repositories.TagRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {

    private final SkillRepository skillRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final SkillRoutingTagRepository skillRoutingTagRepository;
    private final SkillRoutingDepartmentRepository skillRoutingDepartmentRepository;
    private final TagRepository tagRepository;
    private final DepartmentRepository departmentRepository;
    private final AgentProfileRepository agentProfileRepository;

    public SkillService(
            SkillRepository skillRepository,
            AgentSkillRepository agentSkillRepository,
            SkillRoutingTagRepository skillRoutingTagRepository,
            SkillRoutingDepartmentRepository skillRoutingDepartmentRepository,
            TagRepository tagRepository,
            DepartmentRepository departmentRepository,
            AgentProfileRepository agentProfileRepository) {
        this.skillRepository = skillRepository;
        this.agentSkillRepository = agentSkillRepository;
        this.skillRoutingTagRepository = skillRoutingTagRepository;
        this.skillRoutingDepartmentRepository = skillRoutingDepartmentRepository;
        this.tagRepository = tagRepository;
        this.departmentRepository = departmentRepository;
        this.agentProfileRepository = agentProfileRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listForAdmin() {
        List<Skill> skills = skillRepository.findAll(Sort.by("name"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Skill s : skills) {
            rows.add(toIndexRow(s.getId(), s));
        }
        return Map.of("skills", rows);
    }

    private Map<String, Object> toIndexRow(Long skillId, Skill s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("agents_count", agentSkillRepository.countBySkill_Id(skillId));
        m.put("routing_tags_count", skillRoutingTagRepository.countBySkill_Id(skillId));
        m.put("routing_departments_count", skillRoutingDepartmentRepository.countBySkill_Id(skillId));
        if (s.getUpdatedAt() != null) {
            m.put("updated_at", s.getUpdatedAt().toString());
        }
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFormContext() {
        return buildFormResponse(null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findForEdit(Long id) {
        Skill s = skillRepository
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Skill not found: " + id));
        return buildFormResponse(toEditSkillMap(id, s));
    }

    private Map<String, Object> buildFormResponse(Map<String, Object> skill) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("skill", skill);
        m.put("available_agents", listAvailableAgents());
        m.put("available_tags", listAvailableTags());
        m.put("available_departments", listAvailableDepartments());
        return m;
    }

    private List<Map<String, Object>> listAvailableAgents() {
        return agentProfileRepository.findByActiveTrueAndAgentTrueOrderByName().stream()
                .<Map<String, Object>>map(ap -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("id", ap.getId());
                    row.put("name", ap.getName());
                    row.put("email", ap.getEmail());
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> listAvailableTags() {
        return tagRepository.findAll(Sort.by("name")).stream()
                .<Map<String, Object>>map(t -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("id", t.getId());
                    row.put("name", t.getName());
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> listAvailableDepartments() {
        return departmentRepository.findAll(Sort.by("name")).stream()
                .<Map<String, Object>>map(d -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("id", d.getId());
                    row.put("name", d.getName());
                    return row;
                })
                .toList();
    }

    private Map<String, Object> toEditSkillMap(Long skillId, Skill s) {
        List<Long> tagIds = skillRoutingTagRepository.findBySkill_Id(skillId).stream()
                .map(srt -> srt.getTag().getId())
                .sorted()
                .toList();
        List<Long> deptIds = skillRoutingDepartmentRepository.findBySkill_Id(skillId).stream()
                .map(srd -> srd.getDepartment().getId())
                .sorted()
                .toList();
        List<Map<String, Object>> agents = agentSkillRepository.findBySkill_Id(skillId).stream()
                .<Map<String, Object>>map(as -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("user_id", as.getUserId());
                    row.put("proficiency", as.getProficiency());
                    return row;
                })
                .sorted(Comparator.comparingLong(m -> (Long) m.get("user_id")))
                .toList();
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        if (s.getDescription() != null) {
            m.put("description", s.getDescription());
        }
        m.put("routing_tag_ids", tagIds);
        m.put("routing_department_ids", deptIds);
        m.put("agents", agents);
        return m;
    }

    @Transactional
    public Skill create(CreateSkillDto dto) {
        validateForCreate(dto);
        Skill s = new Skill();
        s.setName(dto.getName().trim());
        s.setSlug(uniqueSlugFromName(s.getName(), null));
        s.setDescription(dto.getDescription());
        Skill saved = skillRepository.save(s);
        syncRelations(saved.getId(), dto);
        return skillRepository
                .findById(saved.getId())
                .orElseThrow(() -> new EntityNotFoundException("Skill not found: " + saved.getId()));
    }

    @Transactional
    public Skill update(long id, UpdateSkillDto dto) {
        if (!skillRepository.existsById(id)) {
            throw new EntityNotFoundException("Skill not found: " + id);
        }
        validateForUpdate(id, dto);
        Skill s = skillRepository.findById(id).orElseThrow();
        s.setName(dto.getName().trim());
        s.setSlug(uniqueSlugFromName(s.getName(), id));
        s.setDescription(dto.getDescription());
        skillRepository.save(s);
        syncRelations(id, dto);
        return skillRepository
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Skill not found: " + id));
    }

    @Transactional
    public void delete(long id) {
        if (!skillRepository.existsById(id)) {
            throw new EntityNotFoundException("Skill not found: " + id);
        }
        skillRepository.deleteById(id);
    }

    private void validateForCreate(CreateSkillDto dto) {
        if (skillRepository.existsByName(dto.getName().trim())) {
            throw new IllegalArgumentException("A skill with this name already exists.");
        }
        validateRelations(dto);
    }

    private void validateForUpdate(long id, CreateSkillDto dto) {
        if (skillRepository.existsByNameAndIdNot(dto.getName().trim(), id)) {
            throw new IllegalArgumentException("A skill with this name already exists.");
        }
        validateRelations(dto);
    }

    private void validateRelations(CreateSkillDto dto) {
        if (dto.getRoutingTagIds() != null) {
            for (Long tid : dto.getRoutingTagIds()) {
                tagRepository
                        .findById(tid)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown tag: " + tid));
            }
        }
        if (dto.getRoutingDepartmentIds() != null) {
            for (Long did : dto.getRoutingDepartmentIds()) {
                departmentRepository
                        .findById(did)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown department: " + did));
            }
        }
        if (dto.getAgents() != null) {
            for (AgentSkillEntryDto e : dto.getAgents()) {
                AgentProfile ap = agentProfileRepository
                        .findById(e.getUserId())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + e.getUserId()));
                if (!ap.isAgent()) {
                    throw new IllegalArgumentException("User " + e.getUserId() + " is not an agent.");
                }
                int p = e.getProficiency();
                if (p < 1 || p > 5) {
                    throw new IllegalArgumentException("Proficiency must be between 1 and 5.");
                }
            }
        }
    }

    private void syncRelations(long skillId, CreateSkillDto dto) {
        Skill skillRef = skillRepository.getReferenceById(skillId);
        skillRoutingTagRepository.deleteBySkill_Id(skillId);
        skillRoutingDepartmentRepository.deleteBySkill_Id(skillId);
        agentSkillRepository.deleteBySkill_Id(skillId);

        if (dto.getRoutingTagIds() != null) {
            for (Long tid : dto.getRoutingTagIds()) {
                Tag tag = tagRepository.getReferenceById(tid);
                SkillRoutingTag link = new SkillRoutingTag();
                link.setSkill(skillRef);
                link.setTag(tag);
                skillRoutingTagRepository.save(link);
            }
        }
        if (dto.getRoutingDepartmentIds() != null) {
            for (Long did : dto.getRoutingDepartmentIds()) {
                Department dept = departmentRepository.getReferenceById(did);
                SkillRoutingDepartment link = new SkillRoutingDepartment();
                link.setSkill(skillRef);
                link.setDepartment(dept);
                skillRoutingDepartmentRepository.save(link);
            }
        }
        if (dto.getAgents() != null) {
            Map<Long, AgentSkillEntryDto> byUser = dto.getAgents().stream()
                    .collect(Collectors.toMap(AgentSkillEntryDto::getUserId, e -> e, (a, b) -> b));
            for (AgentSkillEntryDto e : byUser.values()) {
                AgentSkill row = new AgentSkill();
                row.setUserId(e.getUserId());
                row.setSkill(skillRef);
                row.setProficiency(e.getProficiency());
                agentSkillRepository.save(row);
            }
        }
    }

    private String uniqueSlugFromName(String name, Long excludeSkillId) {
        String base = slugify(name);
        String candidate = base;
        int i = 2;
        while (true) {
            boolean taken = excludeSkillId == null
                    ? skillRepository.existsBySlug(candidate)
                    : skillRepository.existsBySlugAndIdNot(candidate, excludeSkillId);
            if (!taken) {
                return candidate;
            }
            candidate = base + "-" + i++;
        }
    }

    private static String slugify(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("^-+", "").replaceAll("-+$", "");
        return s.isEmpty() ? "skill" : s;
    }
}
