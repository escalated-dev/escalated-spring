package dev.escalated.repositories;

import dev.escalated.models.GuestVerification;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GuestVerificationRepository extends JpaRepository<GuestVerification, Long> {

    Optional<GuestVerification> findByPublicId(String publicId);

    long countByEmailDigestAndCreatedAtAfter(String emailDigest, Instant since);

    long countByEmailDigestAndClientDigestAndCreatedAtAfter(String emailDigest, String clientDigest, Instant since);

    /** Spends the code if it is live, unspent, under its guess limit and matches. 1 when it did. */
    @Modifying
    @Query("UPDATE GuestVerification v SET v.consumedAt = :now WHERE v.publicId = :id AND v.consumedAt IS NULL"
            + " AND v.attempts < :maxAttempts AND v.expiresAt > :now AND v.codeDigest = :code"
            + " AND v.emailDigest = :email AND v.purpose = :purpose")
    int claim(@Param("id") String id, @Param("code") String codeDigest, @Param("email") String emailDigest,
              @Param("purpose") String purpose, @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Query("UPDATE GuestVerification v SET v.attempts = v.attempts + 1 WHERE v.publicId = :id AND v.consumedAt IS NULL")
    int countGuess(@Param("id") String id);

    @Modifying
    @Query("UPDATE GuestVerification v SET v.consumedAt = NULL WHERE v.publicId = :id")
    int release(@Param("id") String id);

    @Modifying
    @Query("DELETE FROM GuestVerification v WHERE v.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
