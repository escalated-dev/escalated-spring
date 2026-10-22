package dev.escalated.repositories;

import dev.escalated.models.SlaPolicy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    List<SlaPolicy> findByActiveTrueOrderByName();

    /** The active policies, oldest first: the order ties are broken in. */
    List<SlaPolicy> findByActiveTrueOrderByIdAsc();
}
