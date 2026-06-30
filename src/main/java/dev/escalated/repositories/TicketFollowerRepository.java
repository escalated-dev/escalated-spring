package dev.escalated.repositories;

import dev.escalated.models.TicketFollower;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketFollowerRepository extends JpaRepository<TicketFollower, Long> {

    boolean existsByTicketIdAndUserId(Long ticketId, String userId);

    List<TicketFollower> findByTicketId(Long ticketId);
}
