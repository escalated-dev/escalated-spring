package dev.escalated.repositories;

import dev.escalated.models.TicketSubjectLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketSubjectLinkRepository extends JpaRepository<TicketSubjectLink, Long> {

    List<TicketSubjectLink> findByTicketIdOrderByPositionAsc(Long ticketId);

    Optional<TicketSubjectLink> findByTicketIdAndSubjectTypeAndSubjectId(
            Long ticketId, String subjectType, String subjectId);

    Optional<TicketSubjectLink> findByIdAndTicketId(Long id, Long ticketId);

    void deleteByTicketId(Long ticketId);

    @Query("SELECT COALESCE(MAX(l.position), -1) FROM TicketSubjectLink l WHERE l.ticket.id = :ticketId")
    int findMaxPositionByTicketId(@Param("ticketId") Long ticketId);
}
