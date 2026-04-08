package dev.escalated.repositories;

import dev.escalated.models.AgentCapacity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentCapacityRepository extends JpaRepository<AgentCapacity, Long> {

    Optional<AgentCapacity> findByAgentId(Long agentId);
}
