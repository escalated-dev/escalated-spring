package dev.escalated.services;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.models.AgentCapacity;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AgentSkillRepository;
import dev.escalated.repositories.SkillRoutingDepartmentRepository;
import dev.escalated.repositories.SkillRoutingTagRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillRoutingServiceTest {

    @Mock
    private SkillRoutingTagRepository skillRoutingTagRepository;

    @Mock
    private SkillRoutingDepartmentRepository skillRoutingDepartmentRepository;

    @Mock
    private AgentSkillRepository agentSkillRepository;

    @Mock
    private AgentProfileRepository agentProfileRepository;

    private SkillRoutingService skillRoutingService;

    @BeforeEach
    void setUp() {
        skillRoutingService = new SkillRoutingService(
                skillRoutingTagRepository,
                skillRoutingDepartmentRepository,
                agentSkillRepository,
                agentProfileRepository);
    }

    @Test
    void findMatchingAgents_intersectsSkillsFromTagsAndDepartments() {
        Tag bug = new Tag();
        bug.setId(7L);
        Department support = new Department();
        support.setId(3L);
        Ticket ticket = new Ticket();
        ticket.setTags(Set.of(bug));
        ticket.setDepartment(support);

        Mockito.when(skillRoutingTagRepository.findSkillIdsByTagIds(Set.of(7L)))
                .thenReturn(List.of(100L));
        Mockito.when(skillRoutingDepartmentRepository.findSkillIdsByDepartmentId(3L))
                .thenReturn(List.of(200L));

        Mockito.when(agentSkillRepository.findUserIdsBySkillId(100L)).thenReturn(List.of(1L, 2L));
        Mockito.when(agentSkillRepository.findUserIdsBySkillId(200L)).thenReturn(List.of(1L));

        Mockito.when(agentSkillRepository.sumProficiencyForUsersAndSkills(Mockito.anySet(), Mockito.anySet()))
                .thenReturn(List.of(new Object[] {1L, 9L}));

        AgentProfile match = agent(1L, 0.2d);
        Mockito.when(agentProfileRepository.findAllById(Mockito.anySet())).thenReturn(List.of(match));

        List<AgentProfile> agents = skillRoutingService.findMatchingAgents(ticket);

        assertThat(agents).extracting(AgentProfile::getId).containsExactly(1L);
    }

    @Test
    void findMatchingAgents_ordersByProficiencyDescThenUtilization() {
        Tag bug = new Tag();
        bug.setId(7L);
        Ticket ticket = new Ticket();
        ticket.setTags(Set.of(bug));
        ticket.setDepartment(null);

        Mockito.when(skillRoutingTagRepository.findSkillIdsByTagIds(Set.of(7L)))
                .thenReturn(List.of(100L, 200L));
        Mockito.when(agentSkillRepository.findUserIdsBySkillId(100L)).thenReturn(List.of(1L, 2L));
        Mockito.when(agentSkillRepository.findUserIdsBySkillId(200L)).thenReturn(List.of(1L, 2L));
        Mockito.when(agentSkillRepository.sumProficiencyForUsersAndSkills(Mockito.anySet(), Mockito.anySet()))
                .thenReturn(List.of(new Object[] {1L, 5L}, new Object[] {2L, 8L}));

        AgentProfile a1 = agent(1L, 0.5d);
        AgentProfile a2 = agent(2L, 0.5d);
        Mockito.when(agentProfileRepository.findAllById(Mockito.anySet())).thenReturn(List.of(a1, a2));

        List<AgentProfile> agents = skillRoutingService.findMatchingAgents(ticket);

        assertThat(agents).extracting(AgentProfile::getId).containsExactly(2L, 1L);
    }

    @Test
    void findMatchingAgents_returnsEmptyWhenNoRulesMatch() {
        Ticket ticket = new Ticket();
        ticket.setTags(Set.of());
        ticket.setDepartment(null);
        List<AgentProfile> agents = skillRoutingService.findMatchingAgents(ticket);
        assertThat(agents).isEmpty();
    }

    private static AgentProfile agent(long id, double utilization) {
        AgentProfile ap = new AgentProfile();
        ap.setId(id);
        ap.setActive(true);
        ap.setAvailable(true);
        ap.setAgent(true);
        AgentCapacity wcap = new AgentCapacity();
        wcap.setAgent(ap);
        wcap.setMaxTickets(10);
        wcap.setCurrentTickets((int) Math.round(utilization * 10));
        ap.setCapacity(wcap);
        return ap;
    }
}
