package dev.escalated.repositories;

import dev.escalated.models.KnowledgeBaseCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KnowledgeBaseCategoryRepository extends JpaRepository<KnowledgeBaseCategory, Long> {

    Optional<KnowledgeBaseCategory> findBySlug(String slug);

    List<KnowledgeBaseCategory> findByActiveTrueAndParentIsNullOrderBySortOrderAsc();
}
