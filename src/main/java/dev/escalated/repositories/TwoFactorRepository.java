package dev.escalated.repositories;

import dev.escalated.models.TwoFactor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TwoFactorRepository extends JpaRepository<TwoFactor, Long> {

    Optional<TwoFactor> findByAgentId(Long agentId);

    /**
     * Records {@code step} as the last accepted time step, only when it is later than the one
     * recorded: 1 when it was, 0 when a concurrent request got there first.
     */
    @Modifying
    @Query("UPDATE TwoFactor t SET t.lastUsedStep = :step WHERE t.id = :id"
            + " AND (t.lastUsedStep IS NULL OR t.lastUsedStep < :step)")
    int consumeStep(@Param("id") Long id, @Param("step") Long step);
}
