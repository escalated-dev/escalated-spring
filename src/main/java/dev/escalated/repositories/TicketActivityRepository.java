package dev.escalated.repositories;

import dev.escalated.models.TicketActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketActivityRepository extends JpaRepository<TicketActivity, Long> {

    List<TicketActivity> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
