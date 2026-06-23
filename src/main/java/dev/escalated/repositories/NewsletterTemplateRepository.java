package dev.escalated.repositories;

import dev.escalated.models.newsletter.NewsletterTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsletterTemplateRepository extends JpaRepository<NewsletterTemplate, Long> {

    List<NewsletterTemplate> findAllByOrderByCreatedAtDesc();
}
