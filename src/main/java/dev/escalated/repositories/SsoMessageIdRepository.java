package dev.escalated.repositories;

import dev.escalated.models.SsoMessageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SsoMessageIdRepository extends JpaRepository<SsoMessageId, Long> {

    boolean existsByKindAndMessageId(String kind, String messageId);

    /** Removes the ID if it is still unexpired: 1 when it was there to consume. */
    @Modifying
    @Query("DELETE FROM SsoMessageId m WHERE m.kind = :kind AND m.messageId = :id AND m.expiresAt >= :now")
    int consume(@Param("kind") String kind, @Param("id") String id, @Param("now") long now);

    @Modifying
    @Query("DELETE FROM SsoMessageId m WHERE m.expiresAt < :now")
    int purgeExpired(@Param("now") long now);
}
