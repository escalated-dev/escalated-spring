package dev.escalated.repositories;

import dev.escalated.models.newsletter.NewsletterDelivery;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsletterDeliveryRepository extends JpaRepository<NewsletterDelivery, Long> {

    java.util.Optional<NewsletterDelivery> findByTrackingToken(String trackingToken);

    List<NewsletterDelivery> findByNewsletterIdAndTestFalseOrderByIdDesc(
            Long newsletterId, Pageable pageable);

    List<NewsletterDelivery> findByNewsletterIdAndTestFalseAndStatusOrderByIdDesc(
            Long newsletterId, String status, Pageable pageable);

    long countByNewsletterIdAndStatusIn(Long newsletterId, Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d FROM NewsletterDelivery d
            WHERE d.status = 'pending'
              AND (d.nextAttemptAt IS NULL OR d.nextAttemptAt <= :now)
            ORDER BY d.id ASC
            """)
    List<NewsletterDelivery> findPendingForDispatch(@Param("now") Instant now, Pageable pageable);

    @Query("""
            SELECT d FROM NewsletterDelivery d
            WHERE d.newsletterId = :newsletterId
              AND d.status IN ('sent', 'bounced', 'complained', 'failed')
            ORDER BY d.id ASC
            """)
    List<NewsletterDelivery> findFirstTerminalByNewsletter(
            @Param("newsletterId") Long newsletterId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NewsletterDelivery d
            SET d.status = 'pending', d.claimedAt = NULL
            WHERE d.status = 'queued' AND d.claimedAt < :cutoff
            """)
    int reclaimStuck(@Param("cutoff") Instant cutoff);
}
