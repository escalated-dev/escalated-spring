package dev.escalated.repositories;

import dev.escalated.models.CustomerPresence;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerPresenceRepository extends JpaRepository<CustomerPresence, Long> {

    List<CustomerPresence> findByEmailIn(Collection<String> emails);

    boolean existsByEmail(String email);

    /** Moves last_seen_at forward only when it is older than {@code before}: the 30-second throttle. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CustomerPresence p SET p.lastSeenAt = :now, p.updatedAt = :now WHERE p.email = :email AND p.lastSeenAt < :before")
    int touch(@Param("email") String email, @Param("now") Instant now, @Param("before") Instant before);
}
