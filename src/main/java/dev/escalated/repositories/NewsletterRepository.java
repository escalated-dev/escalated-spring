package dev.escalated.repositories;

import dev.escalated.models.newsletter.Newsletter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsletterRepository extends JpaRepository<Newsletter, Long> {

    List<Newsletter> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses);

    List<Newsletter> findByStatusAndScheduledAtLessThanEqual(String status, Instant scheduledAt);

    List<Newsletter> findByStatus(String status);
}
