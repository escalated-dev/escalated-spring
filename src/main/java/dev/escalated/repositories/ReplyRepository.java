package dev.escalated.repositories;

import dev.escalated.models.Reply;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ReplyRepository extends JpaRepository<Reply, Long>, JpaSpecificationExecutor<Reply> {

    List<Reply> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    List<Reply> findByTicketIdAndInternalFalseOrderByCreatedAtAsc(Long ticketId);

    Optional<Reply> findByEmailMessageId(String emailMessageId);
}
