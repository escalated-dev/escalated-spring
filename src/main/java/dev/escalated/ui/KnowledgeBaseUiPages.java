package dev.escalated.ui;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.KnowledgeBaseArticle;
import dev.escalated.models.KnowledgeBaseCategory;
import dev.escalated.repositories.KnowledgeBaseArticleRepository;
import dev.escalated.repositories.KnowledgeBaseCategoryRepository;
import dev.escalated.services.SettingsService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The knowledge base pages' props and writes, as escalated-laravel's {@code Admin\ArticleController},
 * {@code Admin\ArticleCategoryController} and {@code Customer\KnowledgeBaseController}. An article's
 * {@code status} is {@code published} or {@code draft} (this port's {@code is_published}), its
 * {@code body} is the stored content, and a category's {@code position} is its sort order.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class KnowledgeBaseUiPages {

    private static final int ADMIN_PER_PAGE = 20;
    private static final int CUSTOMER_PER_PAGE = 15;
    private static final Set<String> STATUSES = Set.of("draft", "published");

    private final KnowledgeBaseArticleRepository articles;
    private final KnowledgeBaseCategoryRepository categories;
    private final SettingsService settings;
    private final EscalatedProperties properties;
    private final UiRoutes routes;

    public KnowledgeBaseUiPages(KnowledgeBaseArticleRepository articles, KnowledgeBaseCategoryRepository categories,
                                SettingsService settings, EscalatedProperties properties, UiRoutes routes) {
        this.articles = articles;
        this.categories = categories;
        this.settings = settings;
        this.properties = properties;
        this.routes = routes;
    }

    // ── Admin: articles ─────────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> adminArticles(Map<String, String> filters) {
        String search = blankToNull(filters.get("search"));
        String status = blankToNull(filters.get("status"));
        Long categoryId = UiTicketFilters.parseLong(filters.get("category_id"));
        Specification<KnowledgeBaseArticle> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (search != null) {
                String pattern = like(search);
                where.add(cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("content")), pattern, '\\')));
            }
            if ("published".equals(status) || "draft".equals(status)) {
                where.add(cb.equal(root.get("published"), "published".equals(status)));
            }
            if (categoryId != null) {
                where.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<KnowledgeBaseArticle> page = articles.findAll(spec, PageRequest.of(page(filters) - 1, ADMIN_PER_PAGE,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (KnowledgeBaseArticle article : page) {
            rows.add(article(article));
        }
        Map<String, Object> shown = new LinkedHashMap<>();
        for (String key : List.of("search", "status", "category_id")) {
            if (filters.containsKey(key)) {
                shown.put(key, filters.get(key));
            }
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("articles", UiPresenter.paginator(page, rows, routes.url("escalated.admin.kb-articles.index")));
        props.put("categories", categoryOptions());
        props.put("filters", shown);
        return props;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> articleForm(Long id) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (id != null) {
            props.put("article", article(findArticle(id)));
        }
        props.put("categories", categoryOptions());
        return props;
    }

    /** Validates as Laravel's {@code ArticleController} and saves; a blank slug comes from the title. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void saveArticle(Long id, UiInput input, AgentProfile author) {
        String title = input.requiredString("title", 255);
        String slug = input.string("slug");
        if (slug != null && slug.length() > 255) {
            throw UiInput.invalid("slug", "The slug field must not be greater than 255 characters.");
        }
        slug = slug == null ? slugify(title) : slug;
        String status = input.string("status");
        if (status == null) {
            throw UiInput.invalid("status", "The status field is required.");
        }
        if (!STATUSES.contains(status)) {
            throw UiInput.invalid("status", "The selected status is invalid.");
        }
        KnowledgeBaseCategory category = optionalCategory(input, "category_id");
        String finalSlug = slug;
        articles.findBySlug(slug).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw UiInput.invalid("slug", "The slug has already been taken.");
        });

        KnowledgeBaseArticle article = id == null ? new KnowledgeBaseArticle() : findArticle(id);
        article.setTitle(title);
        article.setSlug(finalSlug);
        String body = input.string("body");
        article.setContent(body == null ? "" : body);
        article.setCategory(category);
        boolean published = "published".equals(status);
        article.setPublished(published);
        if (published && article.getPublishedAt() == null) {
            article.setPublishedAt(Instant.now());
        }
        if (id == null && author != null) {
            article.setAuthorName(author.getName());
        }
        articles.save(article);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteArticle(Long id) {
        articles.delete(findArticle(id));
    }

    // ── Admin: categories ───────────────────────────────────────────────

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> adminCategories() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (KnowledgeBaseCategory category : ordered()) {
            Map<String, Object> row = category(category);
            row.put("articles_count", category.getArticles().size());
            rows.add(row);
        }
        return Map.of("categories", rows);
    }

    /** Validates as Laravel's {@code ArticleCategoryController} and saves; a blank slug comes from the name. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void saveCategory(Long id, UiInput input) {
        String name = input.requiredString("name", 255);
        String slug = input.string("slug");
        if (slug != null && slug.length() > 255) {
            throw UiInput.invalid("slug", "The slug field must not be greater than 255 characters.");
        }
        slug = slug == null ? slugify(name) : slug;
        String description = input.string("description");
        if (description != null && description.length() > 1000) {
            throw UiInput.invalid("description", "The description field must not be greater than 1000 characters.");
        }
        Long position = input.longValue("position");
        if (position != null && position < 0) {
            throw UiInput.invalid("position", "The position field must be at least 0.");
        }
        KnowledgeBaseCategory parent = optionalCategory(input, "parent_id");
        if (parent != null && id != null && createsCycle(parent, id)) {
            throw UiInput.invalid("parent_id", "A category cannot be its own parent.");
        }
        categories.findBySlug(slug).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw UiInput.invalid("slug", "The slug has already been taken.");
        });

        KnowledgeBaseCategory category = id == null ? new KnowledgeBaseCategory() : findCategory(id);
        category.setName(name);
        category.setSlug(slug);
        category.setDescription(description);
        category.setParent(parent);
        category.setSortOrder(position == null ? 0 : position.intValue());
        categories.save(category);
    }

    /** Deletes the category; its articles are left uncategorised and its children become top level. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void deleteCategory(Long id) {
        KnowledgeBaseCategory category = findCategory(id);
        for (KnowledgeBaseArticle article : new ArrayList<>(category.getArticles())) {
            article.setCategory(null);
            articles.save(article);
        }
        for (KnowledgeBaseCategory child : new ArrayList<>(category.getChildren())) {
            child.setParent(null);
            categories.save(child);
        }
        // The collections cascade removal; emptied, the delete takes only the category.
        category.getArticles().clear();
        category.getChildren().clear();
        articles.flush();
        categories.delete(category);
        categories.flush();
    }

    // ── Customer ────────────────────────────────────────────────────────

    /** Whether the knowledge base is switched on ({@code escalated.knowledge-base.enabled}). */
    public boolean enabled() {
        return properties.getKnowledgeBase().isEnabled();
    }

    /** Whether someone not signed in may read it (the {@code knowledge_base_public} setting, default on). */
    public boolean isPublic() {
        return setting("knowledge_base_public", true);
    }

    public boolean feedbackEnabled() {
        return setting("knowledge_base_feedback_enabled", true);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> customerIndex(Map<String, String> filters) {
        List<Map<String, Object>> roots = new ArrayList<>();
        for (KnowledgeBaseCategory category : categories.findByActiveTrueAndParentIsNullOrderBySortOrderAsc()) {
            Map<String, Object> row = category(category);
            row.put("articles_count", category.getArticles().stream().filter(KnowledgeBaseArticle::isPublished).count());
            roots.add(row);
        }
        String search = blankToNull(filters.get("search"));
        Long categoryId = UiTicketFilters.parseLong(filters.get("category"));
        Specification<KnowledgeBaseArticle> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("published")));
            if (search != null) {
                String pattern = like(search);
                where.add(cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("content")), pattern, '\\')));
            }
            if (categoryId != null) {
                where.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<KnowledgeBaseArticle> page = articles.findAll(spec, PageRequest.of(page(filters) - 1, CUSTOMER_PER_PAGE,
                Sort.by(Sort.Direction.DESC, "publishedAt", "id")));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (KnowledgeBaseArticle article : page) {
            rows.add(article(article));
        }
        Map<String, Object> shown = new LinkedHashMap<>();
        for (String key : List.of("search", "category")) {
            if (filters.containsKey(key)) {
                shown.put(key, filters.get(key));
            }
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("categories", roots);
        props.put("articles", UiPresenter.paginator(page, rows, routes.url("escalated.customer.kb.index")));
        props.put("filters", shown);
        props.put("feedbackEnabled", feedbackEnabled());
        return props;
    }

    /** A published article, counted as viewed. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public Map<String, Object> customerArticle(String slug) {
        KnowledgeBaseArticle article = published(slug);
        article.setViewCount(article.getViewCount() + 1);
        articles.save(article);
        List<Map<String, Object>> related = new ArrayList<>();
        if (article.getCategory() != null) {
            for (KnowledgeBaseArticle other
                    : articles.findByCategoryIdAndPublishedTrueOrderBySortOrderAsc(article.getCategory().getId())) {
                if (!other.getId().equals(article.getId()) && related.size() < 5) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", other.getId());
                    row.put("title", other.getTitle());
                    row.put("slug", other.getSlug());
                    related.add(row);
                }
            }
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("article", article(article));
        props.put("related", related);
        props.put("feedbackEnabled", feedbackEnabled());
        return props;
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED)
    public void feedback(String slug, UiInput input) {
        Object helpful = input.raw("helpful");
        if (helpful == null || !List.of("true", "false", "1", "0").contains(String.valueOf(helpful).toLowerCase(Locale.ROOT))) {
            throw UiInput.invalid("helpful", helpful == null ? "The helpful field is required."
                    : "The helpful field must be true or false.");
        }
        KnowledgeBaseArticle article = published(slug);
        if (input.bool("helpful", false)) {
            article.setHelpfulCount(article.getHelpfulCount() + 1);
        } else {
            article.setNotHelpfulCount(article.getNotHelpfulCount() + 1);
        }
        articles.save(article);
    }

    // ── Shapes ──────────────────────────────────────────────────────────

    private static Map<String, Object> article(KnowledgeBaseArticle article) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", article.getId());
        map.put("title", article.getTitle());
        map.put("slug", article.getSlug());
        map.put("body", article.getContent());
        map.put("excerpt", article.getExcerpt());
        map.put("status", article.isPublished() ? "published" : "draft");
        map.put("category_id", article.getCategory() == null ? null : article.getCategory().getId());
        map.put("category", article.getCategory() == null ? null : categoryRef(article.getCategory()));
        map.put("author_name", article.getAuthorName());
        map.put("view_count", article.getViewCount());
        map.put("helpful_count", article.getHelpfulCount());
        map.put("not_helpful_count", article.getNotHelpfulCount());
        map.put("published_at", UiPresenter.iso(article.getPublishedAt()));
        map.put("created_at", UiPresenter.iso(article.getCreatedAt()));
        map.put("updated_at", UiPresenter.iso(article.getUpdatedAt()));
        return map;
    }

    private static Map<String, Object> category(KnowledgeBaseCategory category) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", category.getId());
        map.put("name", category.getName());
        map.put("slug", category.getSlug());
        map.put("description", category.getDescription());
        map.put("parent_id", category.getParent() == null ? null : category.getParent().getId());
        map.put("position", category.getSortOrder());
        return map;
    }

    private static Map<String, Object> categoryRef(KnowledgeBaseCategory category) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", category.getId());
        map.put("name", category.getName());
        return map;
    }

    private List<Map<String, Object>> categoryOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        for (KnowledgeBaseCategory category : ordered()) {
            options.add(categoryRef(category));
        }
        return options;
    }

    private List<KnowledgeBaseCategory> ordered() {
        List<KnowledgeBaseCategory> all = new ArrayList<>(categories.findAll());
        all.sort(Comparator.comparingInt(KnowledgeBaseCategory::getSortOrder).thenComparing(KnowledgeBaseCategory::getName));
        return all;
    }

    private KnowledgeBaseCategory optionalCategory(UiInput input, String key) {
        Long categoryId = input.longValue(key);
        if (categoryId == null) {
            return null;
        }
        return categories.findById(categoryId)
                .orElseThrow(() -> UiInput.invalid(key, "The selected " + key.replace('_', ' ') + " is invalid."));
    }

    private boolean createsCycle(KnowledgeBaseCategory parent, Long id) {
        for (KnowledgeBaseCategory at = parent; at != null; at = at.getParent()) {
            if (id.equals(at.getId())) {
                return true;
            }
        }
        return false;
    }

    private KnowledgeBaseArticle published(String slug) {
        return articles.findBySlug(slug).filter(KnowledgeBaseArticle::isPublished)
                .orElseThrow(() -> new EntityNotFoundException("Article not found"));
    }

    private KnowledgeBaseArticle findArticle(Long id) {
        return articles.findById(id).orElseThrow(() -> new EntityNotFoundException("Article not found"));
    }

    private KnowledgeBaseCategory findCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> new EntityNotFoundException("Category not found"));
    }

    private boolean setting(String key, boolean fallback) {
        return settings.get(key).map(v -> "1".equals(v) || "true".equalsIgnoreCase(v)).orElse(fallback);
    }

    private static int page(Map<String, String> filters) {
        Long page = UiTicketFilters.parseLong(filters.get("page"));
        return page == null || page < 1 ? 1 : (int) Math.min(page, Integer.MAX_VALUE);
    }

    private static String like(String search) {
        return "%" + search.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** As Laravel's {@code Str::slug}: ASCII, lower case, words joined by dashes. */
    static String slugify(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "article" : slug;
    }
}
