package dev.escalated.services;

import dev.escalated.models.KnowledgeBaseArticle;
import dev.escalated.models.KnowledgeBaseCategory;
import dev.escalated.repositories.KnowledgeBaseArticleRepository;
import dev.escalated.repositories.KnowledgeBaseCategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseArticleRepository articleRepository;
    private final KnowledgeBaseCategoryRepository categoryRepository;

    public KnowledgeBaseService(KnowledgeBaseArticleRepository articleRepository,
                                KnowledgeBaseCategoryRepository categoryRepository) {
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseCategory> findRootCategories() {
        return categoryRepository.findByActiveTrueAndParentIsNullOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseCategory findCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + slug));
    }

    @Transactional
    public KnowledgeBaseCategory createCategory(String name, String slug, String description, Long parentId) {
        KnowledgeBaseCategory category = new KnowledgeBaseCategory();
        category.setName(name);
        category.setSlug(slug);
        category.setDescription(description);
        if (parentId != null) {
            category.setParent(categoryRepository.findById(parentId)
                    .orElseThrow(() -> new EntityNotFoundException("Parent category not found: " + parentId)));
        }
        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseArticle findArticleBySlug(String slug) {
        return articleRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Article not found: " + slug));
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeBaseArticle> findPublishedArticles(Pageable pageable) {
        return articleRepository.findByPublishedTrueOrderByPublishedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseArticle> findArticlesByCategory(Long categoryId) {
        return articleRepository.findByCategoryIdAndPublishedTrueOrderBySortOrderAsc(categoryId);
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeBaseArticle> searchArticles(String query, Pageable pageable) {
        return articleRepository.searchPublished(query, pageable);
    }

    @Transactional
    public KnowledgeBaseArticle createArticle(String title, String slug, String content,
                                               String excerpt, Long categoryId, String authorName) {
        KnowledgeBaseArticle article = new KnowledgeBaseArticle();
        article.setTitle(title);
        article.setSlug(slug);
        article.setContent(content);
        article.setExcerpt(excerpt);
        article.setAuthorName(authorName);
        if (categoryId != null) {
            article.setCategory(categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new EntityNotFoundException("Category not found: " + categoryId)));
        }
        return articleRepository.save(article);
    }

    @Transactional
    public KnowledgeBaseArticle publishArticle(Long id) {
        KnowledgeBaseArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Article not found: " + id));
        article.setPublished(true);
        article.setPublishedAt(Instant.now());
        return articleRepository.save(article);
    }

    @Transactional
    public void recordView(Long id) {
        articleRepository.findById(id).ifPresent(article -> {
            article.setViewCount(article.getViewCount() + 1);
            articleRepository.save(article);
        });
    }

    @Transactional
    public void recordFeedback(Long id, boolean helpful) {
        articleRepository.findById(id).ifPresent(article -> {
            if (helpful) {
                article.setHelpfulCount(article.getHelpfulCount() + 1);
            } else {
                article.setNotHelpfulCount(article.getNotHelpfulCount() + 1);
            }
            articleRepository.save(article);
        });
    }
}
