package dev.escalated.controllers.admin;

import dev.escalated.models.KnowledgeBaseArticle;
import dev.escalated.models.KnowledgeBaseCategory;
import dev.escalated.services.KnowledgeBaseService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/admin/kb")
public class AdminKnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public AdminKnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<KnowledgeBaseCategory>> categories() {
        return ResponseEntity.ok(knowledgeBaseService.findRootCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<KnowledgeBaseCategory> createCategory(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(knowledgeBaseService.createCategory(
                (String) body.get("name"),
                (String) body.get("slug"),
                (String) body.get("description"),
                body.get("parent_id") != null ? Long.valueOf(body.get("parent_id").toString()) : null));
    }

    @PostMapping("/articles")
    public ResponseEntity<KnowledgeBaseArticle> createArticle(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(knowledgeBaseService.createArticle(
                (String) body.get("title"),
                (String) body.get("slug"),
                (String) body.get("content"),
                (String) body.get("excerpt"),
                body.get("category_id") != null ? Long.valueOf(body.get("category_id").toString()) : null,
                (String) body.get("author_name")));
    }

    @PostMapping("/articles/{id}/publish")
    public ResponseEntity<KnowledgeBaseArticle> publishArticle(@PathVariable Long id) {
        return ResponseEntity.ok(knowledgeBaseService.publishArticle(id));
    }
}
