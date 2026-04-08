package dev.escalated.repositories;

import dev.escalated.models.TicketLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketLinkRepository extends JpaRepository<TicketLink, Long> {

    @Query("SELECT tl FROM TicketLink tl WHERE tl.sourceTicket.id = :ticketId OR tl.targetTicket.id = :ticketId")
    List<TicketLink> findByTicketId(@Param("ticketId") Long ticketId);

    List<TicketLink> findBySourceTicketId(Long sourceTicketId);
}
