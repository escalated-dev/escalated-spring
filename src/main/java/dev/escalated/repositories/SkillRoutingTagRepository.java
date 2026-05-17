package dev.escalated.repositories;

import dev.escalated.models.SkillRoutingTag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillRoutingTagRepository extends JpaRepository<SkillRoutingTag, Long> {

    List<SkillRoutingTag> findBySkill_Id(Long skillId);

    long countBySkill_Id(Long skillId);

    void deleteBySkill_Id(Long skillId);

    @Query("SELECT DISTINCT s.skill.id FROM SkillRoutingTag s WHERE s.tag.id IN :tagIds")
    List<Long> findSkillIdsByTagIds(@Param("tagIds") Collection<Long> tagIds);
}
