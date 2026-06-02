package dev.escalated.repositories;

import dev.escalated.models.newsletter.NewsletterListMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsletterListMemberRepository extends JpaRepository<NewsletterListMember, Long> {

    List<NewsletterListMember> findByListId(Long listId);

    long countByListId(Long listId);

    Optional<NewsletterListMember> findByListIdAndContactId(Long listId, Long contactId);

    void deleteByListIdAndContactId(Long listId, Long contactId);
}
