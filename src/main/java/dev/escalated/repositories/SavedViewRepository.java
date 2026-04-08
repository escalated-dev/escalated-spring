package dev.escalated.repositories;

import dev.escalated.models.SavedView;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedViewRepository extends JpaRepository<SavedView, Long> {

    @Query("SELECT sv FROM SavedView sv WHERE sv.shared = true OR sv.agent.id = :agentId ORDER BY sv.name")
    List<SavedView> findAccessibleByAgent(@Param("agentId") Long agentId);

    List<SavedView> findByAgentIdOrderByName(Long agentId);
}
