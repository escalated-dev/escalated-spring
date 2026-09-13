package dev.escalated.repositories;

import dev.escalated.models.ApiToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    /**
     * Loads the token's agent in the same query. The token is read by the
     * authentication filter, which runs outside any transaction or open
     * session, so a lazy agent there is an exception rather than a query.
     */
    @EntityGraph(attributePaths = "agent")
    Optional<ApiToken> findByTokenHash(String tokenHash);

    List<ApiToken> findByAgentIdOrderByCreatedAtDesc(Long agentId);
}
