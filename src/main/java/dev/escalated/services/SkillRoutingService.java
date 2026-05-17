package dev.escalated.services;

import dev.escalated.models.AgentCapacity;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Tag;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.AgentSkillRepository;
import dev.escalated.repositories.SkillRoutingDepartmentRepository;
import dev.escalated.repositories.SkillRoutingTagRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Selects agents for a ticket using explicit skill–tag and skill–department
 * routing mappings plus per-agent proficiency (skills-management contract).
 */
@Service
public class SkillRoutingService {

    private final SkillRoutingTagRepository skillRoutingTagRepository;
    private final SkillRoutingDepartmentRepository skillRoutingDepartmentRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final AgentProfileRepository agentProfileRepository;

    public SkillRoutingService(
            SkillRoutingTagRepository skillRoutingTagRepository,
            SkillRoutingDepartmentRepository skillRoutingDepartmentRepository,
            AgentSkillRepository agentSkillRepository,
            AgentProfileRepository agentProfileRepository) {
        this.skillRoutingTagRepository = skillRoutingTagRepository;
        this.skillRoutingDepartmentRepository = skillRoutingDepartmentRepository;
        this.agentSkillRepository = agentSkillRepository;
        this.agentProfileRepository = agentProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<AgentProfile> findMatchingAgents(Ticket ticket) {
        Set<Long> required = new LinkedHashSet<>();
        Set<Long> tagIds = ticket.getTags().stream().map(Tag::getId).collect(Collectors.toSet());
        if (!tagIds.isEmpty()) {
            required.addAll(skillRoutingTagRepository.findSkillIdsByTagIds(tagIds));
        }
        if (ticket.getDepartment() != null) {
            required.addAll(skillRoutingDepartmentRepository.findSkillIdsByDepartmentId(
                    ticket.getDepartment().getId()));
        }
        if (required.isEmpty()) {
            return List.of();
        }

        List<Long> skillList = new ArrayList<>(required);
        Set<Long> candidates = new LinkedHashSet<>(agentSkillRepository.findUserIdsBySkillId(skillList.get(0)));
        for (int i = 1; i < skillList.size(); i++) {
            candidates.retainAll(new HashSet<>(agentSkillRepository.findUserIdsBySkillId(skillList.get(i))));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> proficiencySum = new HashMap<>();
        for (Object[] row : agentSkillRepository.sumProficiencyForUsersAndSkills(candidates, required)) {
            proficiencySum.put((Long) row[0], ((Number) row[1]).intValue());
        }

        List<AgentProfile> profiles = agentProfileRepository.findAllById(candidates);
        return profiles.stream()
                .filter(ap -> ap.isActive() && ap.isAvailable())
                .filter(ap -> {
                    AgentCapacity cap = ap.getCapacity();
                    return cap == null || cap.hasCapacity();
                })
                .sorted(Comparator
                        .comparing((AgentProfile ap) ->
                                proficiencySum.getOrDefault(ap.getId(), 0))
                        .reversed()
                        .thenComparingDouble(ap -> {
                            AgentCapacity cap = ap.getCapacity();
                            return cap != null ? cap.getUtilization() : 0.0;
                        }))
                .toList();
    }
}
