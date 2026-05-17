package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.escalated.dtos.admin.AgentSkillEntryDto;
import dev.escalated.dtos.admin.CreateSkillDto;
import dev.escalated.dtos.admin.UpdateSkillDto;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.AgentSkill;
import dev.escalated.models.Skill;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AgentSkillRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.SkillRepository;
import dev.escalated.repositories.SkillRoutingDepartmentRepository;
import dev.escalated.repositories.SkillRoutingTagRepository;
import dev.escalated.repositories.TagRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private AgentSkillRepository agentSkillRepository;

    @Mock
    private SkillRoutingTagRepository skillRoutingTagRepository;

    @Mock
    private SkillRoutingDepartmentRepository skillRoutingDepartmentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private AgentProfileRepository agentProfileRepository;

    private SkillService skillService;

    @BeforeEach
    void setUp() {
        skillService = new SkillService(
                skillRepository,
                agentSkillRepository,
                skillRoutingTagRepository,
                skillRoutingDepartmentRepository,
                tagRepository,
                departmentRepository,
                agentProfileRepository);
    }

    @Test
    void create_wipesAndSyncsAgentSkillsWithProficiency() {
        CreateSkillDto dto = new CreateSkillDto();
        dto.setName("Networking");

        AgentSkillEntryDto row = new AgentSkillEntryDto();
        row.setUserId(10L);
        row.setProficiency(4);
        dto.setAgents(List.of(row));

        AgentProfile agent = new AgentProfile();
        agent.setId(10L);
        agent.setAgent(true);

        when(skillRepository.existsByName("Networking")).thenReturn(false);
        when(agentProfileRepository.findById(10L)).thenReturn(Optional.of(agent));
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> {
            Skill s = inv.getArgument(0);
            s.setId(44L);
            return s;
        });
        when(skillRepository.findById(44L)).thenAnswer(inv -> {
            Skill s = new Skill();
            s.setId(44L);
            s.setName("Networking");
            s.setSlug("networking");
            return Optional.of(s);
        });
        when(skillRepository.existsBySlug("networking")).thenReturn(false);
        when(skillRepository.getReferenceById(44L)).thenReturn(new Skill());

        Skill created = skillService.create(dto);

        assertThat(created.getId()).isEqualTo(44L);
        verify(skillRoutingTagRepository).deleteBySkill_Id(44L);
        verify(skillRoutingDepartmentRepository).deleteBySkill_Id(44L);
        verify(agentSkillRepository).deleteBySkill_Id(44L);

        ArgumentCaptor<AgentSkill> captor = ArgumentCaptor.forClass(AgentSkill.class);
        verify(agentSkillRepository).save(captor.capture());
        assertThat(captor.getValue().getProficiency()).isEqualTo(4);
        assertThat(captor.getValue().getUserId()).isEqualTo(10L);
    }

    @Test
    void update_throwsWhenSkillMissing() {
        UpdateSkillDto dto = new UpdateSkillDto();
        dto.setName("X");
        when(skillRepository.existsById(9L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> skillService.update(9L, dto));
    }

    @Test
    void delete_throwsWhenSkillMissing() {
        when(skillRepository.existsById(3L)).thenReturn(false);
        assertThrows(EntityNotFoundException.class, () -> skillService.delete(3L));
    }

    @Test
    void create_validatesTagReferences() {
        CreateSkillDto dto = new CreateSkillDto();
        dto.setName("T");
        dto.setRoutingTagIds(List.of(99L));
        when(skillRepository.existsByName("T")).thenReturn(false);
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> skillService.create(dto));
    }
}
