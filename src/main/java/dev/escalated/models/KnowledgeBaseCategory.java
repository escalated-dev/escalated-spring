package dev.escalated.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "escalated_kb_categories")
public class KnowledgeBaseCategory extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private KnowledgeBaseCategory parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<KnowledgeBaseCategory> children = new ArrayList<>();

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    private List<KnowledgeBaseArticle> articles = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public KnowledgeBaseCategory getParent() {
        return parent;
    }

    public void setParent(KnowledgeBaseCategory parent) {
        this.parent = parent;
    }

    public List<KnowledgeBaseCategory> getChildren() {
        return children;
    }

    public void setChildren(List<KnowledgeBaseCategory> children) {
        this.children = children;
    }

    public List<KnowledgeBaseArticle> getArticles() {
        return articles;
    }

    public void setArticles(List<KnowledgeBaseArticle> articles) {
        this.articles = articles;
    }
}
