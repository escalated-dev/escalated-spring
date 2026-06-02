package dev.escalated.controllers.admin;

import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.filterJsonFromBody;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.inertia;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.optionalString;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.redirect;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.requiredString;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.controllers.newsletter.NewsletterHttpSupport;
import dev.escalated.models.newsletter.NewsletterTemplate;
import dev.escalated.repositories.NewsletterTemplateRepository;
import dev.escalated.services.newsletter.NewsletterPermissionService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/admin/newsletters/templates")
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class AdminNewsletterTemplateController {

    private final EscalatedProperties properties;
    private final NewsletterPermissionService permissions;
    private final NewsletterTemplateRepository templates;

    public AdminNewsletterTemplateController(
            EscalatedProperties properties,
            NewsletterPermissionService permissions,
            NewsletterTemplateRepository templates) {
        this.properties = properties;
        this.permissions = permissions;
        this.templates = templates;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> index(Authentication auth) {
        permissions.require(auth, "newsletters.manage");
        return ResponseEntity.ok(inertia(
                "Escalated/Admin/Newsletters/Templates/Index",
                Map.of("templates", templates.findAllByOrderByCreatedAtDesc())));
    }

    @GetMapping("/new")
    public ResponseEntity<Map<String, Object>> create(Authentication auth) {
        permissions.require(auth, "newsletters.manage");
        return ResponseEntity.ok(inertia(
                "Escalated/Admin/Newsletters/Templates/Create",
                Map.of("themes", themes())));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(Authentication auth, @RequestBody Map<String, Object> body) {
        permissions.require(auth, "newsletters.manage");
        NewsletterTemplate template = new NewsletterTemplate();
        apply(template, body);
        template.setCreatedBy(userId(auth));
        templates.save(template);
        return ResponseEntity.ok(redirect("/admin/newsletters/templates"));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<Map<String, Object>> show(Authentication auth, @PathVariable Long templateId) {
        permissions.require(auth, "newsletters.manage");
        NewsletterTemplate template = find(templateId);
        return ResponseEntity.ok(inertia(
                "Escalated/Admin/Newsletters/Templates/Show",
                Map.of("template", template, "themes", themes(), "isNew", false)));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<Map<String, Object>> update(
            Authentication auth, @PathVariable Long templateId, @RequestBody Map<String, Object> body) {
        permissions.require(auth, "newsletters.manage");
        NewsletterTemplate template = find(templateId);
        apply(template, body);
        templates.save(template);
        return ResponseEntity.ok(redirect("/admin/newsletters/templates/" + templateId));
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<Map<String, Object>> destroy(Authentication auth, @PathVariable Long templateId) {
        permissions.require(auth, "newsletters.manage");
        templates.delete(find(templateId));
        return ResponseEntity.ok(redirect("/admin/newsletters/templates"));
    }

    private void apply(NewsletterTemplate template, Map<String, Object> body) {
        template.setName(requiredString(body, "name", 255));
        template.setTheme(requiredString(body, "theme", 64));
        template.setSubjectTemplate(optionalString(body, "subject_template", 998));
        template.setBodyMarkdown(requiredString(body, "body_markdown", null));
        template.setMergeFieldsSchema(filterJsonFromBody(body, "merge_fields_schema"));
    }

    private NewsletterTemplate find(Long id) {
        return templates.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Template #" + id + " not found"));
    }

    private List<String> themes() {
        return NewsletterHttpSupport.discoverThemes(Path.of(properties.getNewsletters().getThemesDir()));
    }

    private static String userId(Authentication auth) {
        return auth == null ? null : auth.getName();
    }
}
