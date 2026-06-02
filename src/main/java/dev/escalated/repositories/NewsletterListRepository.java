package dev.escalated.repositories;

import dev.escalated.models.newsletter.NewsletterList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsletterListRepository extends JpaRepository<NewsletterList, Long> {
}
