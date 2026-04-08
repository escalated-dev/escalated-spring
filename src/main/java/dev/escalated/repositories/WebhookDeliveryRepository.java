package dev.escalated.repositories;

import dev.escalated.models.WebhookDelivery;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findByWebhookIdOrderByCreatedAtDesc(Long webhookId);

    List<WebhookDelivery> findByStatus(String status);
}
