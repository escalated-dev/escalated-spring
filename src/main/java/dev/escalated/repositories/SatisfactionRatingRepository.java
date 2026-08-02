package dev.escalated.repositories;

import dev.escalated.models.SatisfactionRating;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SatisfactionRatingRepository extends JpaRepository<SatisfactionRating, Long> {

    List<SatisfactionRating> findByTicketId(Long ticketId);

    Optional<SatisfactionRating> findByAccessToken(String accessToken);

    @Query("SELECT AVG(sr.rating) FROM SatisfactionRating sr")
    Double getAverageRating();

    // ── Reporting aggregations ───────────────────────────────────────────────

    @Query("SELECT AVG(sr.rating) FROM SatisfactionRating sr WHERE sr.createdAt >= :since")
    Double avgRatingSince(@Param("since") Instant since);

    @Query("SELECT COUNT(sr) FROM SatisfactionRating sr WHERE sr.createdAt >= :since")
    long countRatingsSince(@Param("since") Instant since);

    @Query("SELECT sr.rating, COUNT(sr) FROM SatisfactionRating sr "
            + "WHERE sr.createdAt >= :since GROUP BY sr.rating ORDER BY sr.rating")
    List<Object[]> countByRatingSince(@Param("since") Instant since);

    @Query("SELECT sr.createdAt, sr.rating FROM SatisfactionRating sr WHERE sr.createdAt >= :since")
    List<Object[]> ratingRowsSince(@Param("since") Instant since);

    @Query("SELECT t.assignedAgent.id, AVG(sr.rating) FROM SatisfactionRating sr JOIN sr.ticket t "
            + "WHERE t.assignedAgent IS NOT NULL AND sr.createdAt >= :since GROUP BY t.assignedAgent.id")
    List<Object[]> avgRatingByAgentSince(@Param("since") Instant since);
}
