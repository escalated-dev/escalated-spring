package dev.escalated.repositories;

import dev.escalated.models.AgentSkill;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentSkillRepository extends JpaRepository<AgentSkill, Long> {

    List<AgentSkill> findByUserId(Long userId);

    List<AgentSkill> findBySkill_Id(Long skillId);

    void deleteByUserId(Long userId);

    void deleteBySkill_Id(Long skillId);

    void deleteByUserIdAndSkill_Id(Long userId, Long skillId);

    boolean existsByUserIdAndSkill_Id(Long userId, Long skillId);

    long countBySkill_Id(Long skillId);

    @Query("SELECT DISTINCT a.userId FROM AgentSkill a WHERE a.skill.id = :skillId")
    List<Long> findUserIdsBySkillId(@Param("skillId") Long skillId);

    @Query("SELECT a.userId, SUM(a.proficiency) FROM AgentSkill a WHERE a.userId IN :userIds "
            + "AND a.skill.id IN :skillIds GROUP BY a.userId")
    List<Object[]> sumProficiencyForUsersAndSkills(
            @Param("userIds") Collection<Long> userIds,
            @Param("skillIds") Collection<Long> skillIds);
}
