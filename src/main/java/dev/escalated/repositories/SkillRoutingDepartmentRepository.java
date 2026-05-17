package dev.escalated.repositories;

import dev.escalated.models.SkillRoutingDepartment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillRoutingDepartmentRepository extends JpaRepository<SkillRoutingDepartment, Long> {

    List<SkillRoutingDepartment> findBySkill_Id(Long skillId);

    long countBySkill_Id(Long skillId);

    void deleteBySkill_Id(Long skillId);

    @Query("SELECT DISTINCT s.skill.id FROM SkillRoutingDepartment s WHERE s.department.id = :deptId")
    List<Long> findSkillIdsByDepartmentId(@Param("deptId") Long departmentId);
}
