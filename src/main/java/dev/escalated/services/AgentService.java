package dev.escalated.services;

import dev.escalated.models.AgentCapacity;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Skill;
import dev.escalated.repositories.AgentCapacityRepository;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.SkillRepository;
import dev.escalated.repositories.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentService {

    private final AgentProfileRepository agentRepository;
    private final AgentCapacityRepository capacityRepository;
    private final SkillRepository skillRepository;
    private final TicketRepository ticketRepository;

    public AgentService(AgentProfileRepository agentRepository,
                        AgentCapacityRepository capacityRepository,
                        SkillRepository skillRepository,
                        TicketRepository ticketRepository) {
        this.agentRepository = agentRepository;
        this.capacityRepository = capacityRepository;
        this.skillRepository = skillRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public AgentProfile findById(Long id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found: " + id));
    }

    @Transactional(readOnly = true)
    public AgentProfile findByEmail(String email) {
        return agentRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found: " + email));
    }

    @Transactional(readOnly = true)
    public List<AgentProfile> findAll() {
        return agentRepository.findByActiveTrueOrderByName();
    }

    @Transactional(readOnly = true)
    public List<AgentProfile> findByDepartment(Long departmentId) {
        return agentRepository.findByDepartmentIdAndActiveTrueOrderByName(departmentId);
    }

    @Transactional
    public AgentProfile create(String name, String email, Long departmentId, Long roleId) {
        AgentProfile agent = new AgentProfile();
        agent.setName(name);
        agent.setEmail(email);

        if (departmentId != null) {
            agent.setDepartment(new dev.escalated.models.Department());
            agent.getDepartment().setId(departmentId);
        }
        if (roleId != null) {
            agent.setRole(new dev.escalated.models.Role());
            agent.getRole().setId(roleId);
        }

        AgentProfile saved = agentRepository.save(agent);

        AgentCapacity capacity = new AgentCapacity();
        capacity.setAgent(saved);
        capacity.setMaxTickets(20);
        capacityRepository.save(capacity);

        return saved;
    }

    @Transactional
    public AgentProfile update(Long id, String name, String phone, String signature, boolean available) {
        AgentProfile agent = findById(id);
        if (name != null) {
            agent.setName(name);
        }
        agent.setPhone(phone);
        agent.setSignature(signature);
        agent.setAvailable(available);
        return agentRepository.save(agent);
    }

    @Transactional
    public void updateCapacity(Long agentId, int maxTickets, int weight) {
        AgentCapacity capacity = capacityRepository.findByAgentId(agentId)
                .orElseGet(() -> {
                    AgentCapacity nc = new AgentCapacity();
                    nc.setAgent(findById(agentId));
                    return nc;
                });
        capacity.setMaxTickets(maxTickets);
        capacity.setWeight(weight);
        capacity.setCurrentTickets(ticketRepository.countActiveTicketsByAgent(agentId));
        capacityRepository.save(capacity);
    }

    @Transactional
    public void addSkill(Long agentId, Long skillId) {
        AgentProfile agent = findById(agentId);
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new EntityNotFoundException("Skill not found: " + skillId));
        agent.getSkills().add(skill);
        agentRepository.save(agent);
    }

    @Transactional
    public void removeSkill(Long agentId, Long skillId) {
        AgentProfile agent = findById(agentId);
        agent.getSkills().removeIf(s -> s.getId().equals(skillId));
        agentRepository.save(agent);
    }

    @Transactional(readOnly = true)
    public Optional<AgentProfile> findBestAvailableAgent(Long departmentId, Set<Long> requiredSkillIds) {
        List<AgentProfile> candidates;

        if (requiredSkillIds != null && !requiredSkillIds.isEmpty()) {
            Long firstSkillId = requiredSkillIds.iterator().next();
            candidates = agentRepository.findAvailableAgentsWithSkill(firstSkillId);

            if (requiredSkillIds.size() > 1) {
                candidates = candidates.stream()
                        .filter(agent -> {
                            Set<Skill> agentSkills = agent.getSkills();
                            return requiredSkillIds.stream()
                                    .allMatch(sid -> agentSkills.stream().anyMatch(s -> s.getId().equals(sid)));
                        })
                        .toList();
            }
        } else if (departmentId != null) {
            candidates = agentRepository.findByDepartmentIdAndActiveTrueOrderByName(departmentId).stream()
                    .filter(AgentProfile::isAvailable)
                    .toList();
        } else {
            candidates = agentRepository.findByAvailableTrueAndActiveTrueOrderByName();
        }

        return candidates.stream()
                .filter(agent -> {
                    AgentCapacity cap = agent.getCapacity();
                    return cap == null || cap.hasCapacity();
                })
                .min(Comparator.comparingDouble(agent -> {
                    AgentCapacity cap = agent.getCapacity();
                    return cap != null ? cap.getUtilization() : 0.0;
                }));
    }

    @Transactional
    public void refreshCapacityCount(Long agentId) {
        capacityRepository.findByAgentId(agentId).ifPresent(capacity -> {
            int count = ticketRepository.countActiveTicketsByAgent(agentId);
            capacity.setCurrentTickets(count);
            capacityRepository.save(capacity);
        });
    }
}
