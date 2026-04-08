package dev.escalated.repositories;

import dev.escalated.models.KnowledgeBaseArticle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface KnowledgeBaseArticleRepository extends JpaRepository<KnowledgeBaseArticle, Long> {

    Optional<KnowledgeBaseArticle> findBySlug(String slug);

    List<KnowledgeBaseArticle> findByCategoryIdAndPublishedTrueOrderBySortOrderAsc(Long categoryId);

    Page<KnowledgeBaseArticle> findByPublishedTrueOrderByPublishedAtDesc(Pageable pageable);

    @Query("SELECT a FROM KnowledgeBaseArticle a WHERE a.published = true "
            + "AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.content) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<KnowledgeBaseArticle> searchPublished(@Param("query") String query, Pageable pageable);
}
