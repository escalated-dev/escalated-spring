package dev.escalated.repositories;

import dev.escalated.models.AgentProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentProfileRepository extends JpaRepository<AgentProfile, Long> {

    Optional<AgentProfile> findByEmail(String email);

    Optional<AgentProfile> findByUserId(Long userId);

    List<AgentProfile> findByActiveTrueOrderByName();

    List<AgentProfile> findByDepartmentIdAndActiveTrueOrderByName(Long departmentId);

    List<AgentProfile> findByAvailableTrueAndActiveTrueOrderByName();

    @Query("SELECT ap FROM AgentProfile ap JOIN ap.skills s WHERE s.id = :skillId AND ap.active = true AND ap.available = true")
    List<AgentProfile> findAvailableAgentsWithSkill(@Param("skillId") Long skillId);
}
