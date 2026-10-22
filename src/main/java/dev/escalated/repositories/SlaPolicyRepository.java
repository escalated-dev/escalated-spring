package dev.escalated.repositories;

import dev.escalated.models.SlaPolicy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    List<SlaPolicy> findByActiveTrueOrderByName();

    /** The active policies, oldest first: the order ties are broken in. */
    List<SlaPolicy> findByActiveTrueOrderByIdAsc();

    /** Points the policies that follow a schedule back at the default, before it is deleted. */
    @Modifying
    @Query("UPDATE SlaPolicy p SET p.businessSchedule = NULL WHERE p.businessSchedule.id = :scheduleId")
    int detachSchedule(@Param("scheduleId") Long scheduleId);
}
