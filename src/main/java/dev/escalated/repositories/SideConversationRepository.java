package dev.escalated.repositories;

import dev.escalated.models.SideConversation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SideConversationRepository extends JpaRepository<SideConversation, Long> {

    List<SideConversation> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
