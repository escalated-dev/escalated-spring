package dev.escalated.repositories;

import dev.escalated.models.Automation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationRepository extends JpaRepository<Automation, Long> {

    List<Automation> findAllByOrderByPositionAscIdAsc();

    List<Automation> findByActiveTrueOrderByPositionAscIdAsc();
}
