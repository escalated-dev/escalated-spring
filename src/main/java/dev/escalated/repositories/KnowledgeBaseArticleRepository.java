package dev.escalated.repositories;

import dev.escalated.models.KnowledgeBaseArticle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface KnowledgeBaseArticleRepository extends JpaRepository<KnowledgeBaseArticle, Long>,
        JpaSpecificationExecutor<KnowledgeBaseArticle> {

    Optional<KnowledgeBaseArticle> findBySlug(String slug);

    List<KnowledgeBaseArticle> findByCategoryIdAndPublishedTrueOrderBySortOrderAsc(Long categoryId);

    Page<KnowledgeBaseArticle> findByPublishedTrueOrderByPublishedAtDesc(Pageable pageable);

    /** Published articles matching a LIKE pattern (lower-cased, escaped with {@code !}). */
    @Query("SELECT a FROM KnowledgeBaseArticle a WHERE a.published = true "
            + "AND (LOWER(a.title) LIKE :pattern ESCAPE '!' OR LOWER(a.content) LIKE :pattern ESCAPE '!')")
    Page<KnowledgeBaseArticle> searchPublished(@Param("pattern") String pattern, Pageable pageable);

    /**
     * The agents' knowledge-base search (ADR 2026-11-05-agent-panel-data): published articles
     * matching the pattern, title matches first, then the most recently updated.
     */
    @Query("SELECT a FROM KnowledgeBaseArticle a LEFT JOIN FETCH a.category WHERE a.published = true "
            + "AND (LOWER(a.title) LIKE :pattern ESCAPE '!' OR LOWER(a.content) LIKE :pattern ESCAPE '!') "
            + "ORDER BY CASE WHEN LOWER(a.title) LIKE :pattern ESCAPE '!' THEN 0 ELSE 1 END, a.updatedAt DESC, a.id DESC")
    List<KnowledgeBaseArticle> searchForAgents(@Param("pattern") String pattern, Pageable pageable);
}
