package dev.escalated.ui.controllers;

import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.KnowledgeBaseUiPages;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The knowledge base pages, as escalated-laravel's: the admin
 * {@code Escalated/Admin/KnowledgeBase/Articles/Index} and {@code Form} and {@code Categories/Index}
 * (admins only, under {@code /escalated/admin}), and the customer
 * {@code Escalated/Customer/KnowledgeBase/Index} and {@code Article} under {@code /escalated/kb}.
 * The customer pages answer 404 while {@code escalated.knowledge-base.enabled} is off, and 403 to
 * someone not signed in unless the {@code knowledge_base_public} setting is on (its default).
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX)
public class KnowledgeBaseUiController {

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final KnowledgeBaseUiPages pages;
    private final AgentProfileRepository agents;
    private final UiRoutes routes;

    public KnowledgeBaseUiController(EscalatedUiRenderer renderer, Inertia inertia, KnowledgeBaseUiPages pages,
                                     AgentProfileRepository agents, UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.pages = pages;
        this.agents = agents;
        this.routes = routes;
    }

    // ── Admin: articles ─────────────────────────────────────────────────

    @GetMapping("/admin/kb-articles")
    public Object articles(@RequestParam Map<String, String> filters) {
        return renderer.render("Escalated/Admin/KnowledgeBase/Articles/Index", pages.adminArticles(filters));
    }

    @GetMapping("/admin/kb-articles/create")
    public Object createArticle() {
        return renderer.render("Escalated/Admin/KnowledgeBase/Articles/Form", pages.articleForm(null));
    }

    @GetMapping("/admin/kb-articles/{article:\\d+}/edit")
    public Object editArticle(@PathVariable("article") Long id) {
        return renderer.render("Escalated/Admin/KnowledgeBase/Articles/Form", pages.articleForm(id));
    }

    @PostMapping("/admin/kb-articles")
    public ResponseEntity<Void> storeArticle(HttpServletRequest request, Authentication authentication) {
        pages.saveArticle(null, UiInput.of(request), agents.findByEmail(authentication.getName()).orElse(null));
        return toIndex("kb-articles", "Article created.");
    }

    @PutMapping("/admin/kb-articles/{article:\\d+}")
    public ResponseEntity<Void> updateArticle(@PathVariable("article") Long id, HttpServletRequest request) {
        pages.saveArticle(id, UiInput.of(request), null);
        return toIndex("kb-articles", "Article updated.");
    }

    @DeleteMapping("/admin/kb-articles/{article:\\d+}")
    public ResponseEntity<Void> destroyArticle(@PathVariable("article") Long id) {
        pages.deleteArticle(id);
        return toIndex("kb-articles", "Article deleted.");
    }

    // ── Admin: categories ───────────────────────────────────────────────

    @GetMapping("/admin/kb-categories")
    public Object categories() {
        return renderer.render("Escalated/Admin/KnowledgeBase/Categories/Index", pages.adminCategories());
    }

    @PostMapping("/admin/kb-categories")
    public ResponseEntity<Void> storeCategory(HttpServletRequest request) {
        pages.saveCategory(null, UiInput.of(request));
        return toIndex("kb-categories", "Category created.");
    }

    @PutMapping("/admin/kb-categories/{category:\\d+}")
    public ResponseEntity<Void> updateCategory(@PathVariable("category") Long id, HttpServletRequest request) {
        pages.saveCategory(id, UiInput.of(request));
        return toIndex("kb-categories", "Category updated.");
    }

    @DeleteMapping("/admin/kb-categories/{category:\\d+}")
    public ResponseEntity<Void> destroyCategory(@PathVariable("category") Long id) {
        pages.deleteCategory(id);
        return toIndex("kb-categories", "Category deleted.");
    }

    // ── Customer ────────────────────────────────────────────────────────

    @GetMapping("/kb")
    public Object customerIndex(@RequestParam Map<String, String> filters, Authentication authentication) {
        ResponseEntity<Void> refused = refuse(authentication);
        if (refused != null) {
            return refused;
        }
        return renderer.render("Escalated/Customer/KnowledgeBase/Index", pages.customerIndex(filters));
    }

    @GetMapping("/kb/{slug}")
    public Object customerArticle(@PathVariable String slug, Authentication authentication) {
        ResponseEntity<Void> refused = refuse(authentication);
        if (refused != null) {
            return refused;
        }
        return renderer.render("Escalated/Customer/KnowledgeBase/Article", pages.customerArticle(slug));
    }

    @PostMapping("/kb/{slug}/feedback")
    public ResponseEntity<Void> feedback(@PathVariable String slug, HttpServletRequest request,
                                         Authentication authentication) {
        ResponseEntity<Void> refused = refuse(authentication);
        if (refused != null) {
            return refused;
        }
        if (!pages.feedbackEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        pages.feedback(slug, UiInput.of(request));
        inertia.flash("success", "Thank you for your feedback!");
        return inertia.back(routes.url("escalated.customer.kb.show", slug));
    }

    /** As Laravel's {@code ensureKnowledgeBaseAccessible}: null when the reader may continue. */
    private ResponseEntity<Void> refuse(Authentication authentication) {
        if (!pages.enabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        if (!signedIn && !pages.isPublic()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return null;
    }

    private ResponseEntity<Void> toIndex(String plural, String message) {
        inertia.flash("success", message);
        return inertia.redirect(routes.url("escalated.admin." + plural + ".index"));
    }
}
