package dev.escalated.repositories;

import dev.escalated.models.Webhook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    List<Webhook> findByActiveTrueOrderByCreatedAtDesc();

    @Query("SELECT w FROM Webhook w WHERE w.active = true AND w.events LIKE %:event%")
    List<Webhook> findActiveByEvent(@Param("event") String event);
}
