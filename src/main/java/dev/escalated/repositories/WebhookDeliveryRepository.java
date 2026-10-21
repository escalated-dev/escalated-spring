package dev.escalated.repositories;

import dev.escalated.models.WebhookDelivery;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findByWebhookIdOrderByCreatedAtDesc(Long webhookId);

    List<WebhookDelivery> findByStatus(String status);

    Page<WebhookDelivery> findByWebhookId(Long webhookId, Pageable pageable);

    Optional<WebhookDelivery> findFirstByWebhookIdOrderByCreatedAtDescIdDesc(Long webhookId);
}
