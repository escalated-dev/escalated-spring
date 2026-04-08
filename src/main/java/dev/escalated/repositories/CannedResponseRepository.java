package dev.escalated.repositories;

import dev.escalated.models.CannedResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CannedResponseRepository extends JpaRepository<CannedResponse, Long> {

    Optional<CannedResponse> findByShortcut(String shortcut);

    @Query("SELECT cr FROM CannedResponse cr WHERE cr.shared = true OR cr.createdByAgentId = :agentId ORDER BY cr.title")
    List<CannedResponse> findAccessibleByAgent(@Param("agentId") Long agentId);

    List<CannedResponse> findByCategoryOrderByTitle(String category);
}
