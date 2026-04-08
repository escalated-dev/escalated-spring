package dev.escalated.repositories;

import dev.escalated.models.BusinessSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessScheduleRepository extends JpaRepository<BusinessSchedule, Long> {

    List<BusinessSchedule> findByActiveTrueOrderByName();
}
