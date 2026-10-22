package dev.escalated.repositories;

import dev.escalated.models.CustomTicketStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomTicketStatusRepository extends JpaRepository<CustomTicketStatus, Long> {

    Optional<CustomTicketStatus> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<CustomTicketStatus> findAllByOrderByCategoryAscPositionAscIdAsc();

    List<CustomTicketStatus> findByCategoryAndDefaultStatusTrue(String category);

    /** Tickets keep their built-in status when their custom one goes; only the pointer is cleared. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Ticket t SET t.customStatus = null WHERE t.customStatus.id = :id")
    int detachFromTickets(@Param("id") Long id);
}
