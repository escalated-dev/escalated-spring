package dev.escalated.repositories;

import dev.escalated.models.Macro;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MacroRepository extends JpaRepository<Macro, Long> {

    @Query("SELECT m FROM Macro m WHERE m.active = true AND (m.shared = true OR m.createdByAgentId = :agentId) ORDER BY m.sortOrder, m.name")
    List<Macro> findAccessibleByAgent(@Param("agentId") Long agentId);

    List<Macro> findByActiveTrueOrderBySortOrderAsc();
}
