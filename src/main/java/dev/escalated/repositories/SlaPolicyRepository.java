package dev.escalated.repositories;

import dev.escalated.models.SlaPolicy;
import dev.escalated.models.TicketPriority;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    List<SlaPolicy> findByActiveTrueOrderByName();

    Optional<SlaPolicy> findByPriorityAndActiveTrue(TicketPriority priority);
}
