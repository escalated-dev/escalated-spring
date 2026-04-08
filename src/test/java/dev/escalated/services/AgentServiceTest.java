package dev.escalated.services;

import dev.escalated.models.AgentCapacity;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Skill;
import dev.escalated.repositories.AgentCapacityRepository;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.SkillRepository;
import dev.escalated.repositories.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentProfileRepository agentRepository;
    @Mock
    private AgentCapacityRepository capacityRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private TicketRepository ticketRepository;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentService = new AgentService(agentRepository, capacityRepository, skillRepository, ticketRepository);
    }

    @Test
    void findById_shouldReturnAgent() {
        AgentProfile agent = createTestAgent();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

        AgentProfile result = agentService.findById(1L);

        assertEquals("Test Agent", result.getName());
    }

    @Test
    void findById_shouldThrowWhenNotFound() {
        when(agentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> agentService.findById(999L));
    }

    @Test
    void create_shouldCreateAgentWithCapacity() {
        when(agentRepository.save(any(AgentProfile.class))).thenAnswer(inv -> {
            AgentProfile a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });
        when(capacityRepository.save(any(AgentCapacity.class))).thenReturn(new AgentCapacity());

        AgentProfile result = agentService.create("New Agent", "new@test.com", null, null);

        assertNotNull(result);
        assertEquals("New Agent", result.getName());
        verify(capacityRepository).save(any(AgentCapacity.class));
    }

    @Test
    void findBestAvailableAgent_shouldReturnLeastUtilized() {
        AgentProfile agent1 = createTestAgent();
        agent1.setId(1L);
        AgentCapacity cap1 = new AgentCapacity();
        cap1.setAgent(agent1);
        cap1.setMaxTickets(10);
        cap1.setCurrentTickets(8);
        agent1.setCapacity(cap1);

        AgentProfile agent2 = createTestAgent();
        agent2.setId(2L);
        agent2.setName("Agent 2");
        AgentCapacity cap2 = new AgentCapacity();
        cap2.setAgent(agent2);
        cap2.setMaxTickets(10);
        cap2.setCurrentTickets(3);
        agent2.setCapacity(cap2);

        when(agentRepository.findByAvailableTrueAndActiveTrueOrderByName())
                .thenReturn(List.of(agent1, agent2));

        Optional<AgentProfile> result = agentService.findBestAvailableAgent(null, null);

        assertTrue(result.isPresent());
        assertEquals(2L, result.get().getId());
    }

    @Test
    void findBestAvailableAgent_shouldFilterByCapacity() {
        AgentProfile agent = createTestAgent();
        AgentCapacity cap = new AgentCapacity();
        cap.setAgent(agent);
        cap.setMaxTickets(5);
        cap.setCurrentTickets(5);
        agent.setCapacity(cap);

        when(agentRepository.findByAvailableTrueAndActiveTrueOrderByName())
                .thenReturn(List.of(agent));

        Optional<AgentProfile> result = agentService.findBestAvailableAgent(null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void addSkill_shouldAddSkillToAgent() {
        AgentProfile agent = createTestAgent();
        agent.setSkills(new HashSet<>());
        Skill skill = new Skill();
        skill.setId(1L);
        skill.setName("Java");

        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(agentRepository.save(any())).thenReturn(agent);

        agentService.addSkill(1L, 1L);

        assertTrue(agent.getSkills().contains(skill));
    }

    private AgentProfile createTestAgent() {
        AgentProfile agent = new AgentProfile();
        agent.setId(1L);
        agent.setName("Test Agent");
        agent.setEmail("agent@test.com");
        agent.setActive(true);
        agent.setAvailable(true);
        return agent;
    }
}
