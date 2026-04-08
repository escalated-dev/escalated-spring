package dev.escalated.repositories;

import dev.escalated.models.SatisfactionRating;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SatisfactionRatingRepository extends JpaRepository<SatisfactionRating, Long> {

    List<SatisfactionRating> findByTicketId(Long ticketId);

    Optional<SatisfactionRating> findByAccessToken(String accessToken);

    @Query("SELECT AVG(sr.rating) FROM SatisfactionRating sr")
    Double getAverageRating();
}
