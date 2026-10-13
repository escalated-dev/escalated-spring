package dev.escalated.repositories;

import dev.escalated.models.SlaPolicy;
import dev.escalated.models.TicketPriority;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    List<SlaPolicy> findByActiveTrueOrderByName();

    @org.springframework.data.jpa.repository.Query(
            "SELECT p FROM SlaPolicy p WHERE p.priority = :priority AND p.active = true AND"
                    + " p.departmentId IS NULL AND p.ticketType IS NULL ORDER BY p.id")
    List<SlaPolicy> findUntargeted(
            @org.springframework.data.repository.query.Param("priority") TicketPriority priority);

    default Optional<SlaPolicy> findByPriorityAndActiveTrue(TicketPriority priority) {
        return findUntargeted(priority).stream().findFirst();
    }
}
