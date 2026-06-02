package dev.escalated.controllers.admin;

import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.abort422;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.assertEmail;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.assertOneOf;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.discoverThemes;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.inertia;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.optionalDateAfterNow;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.optionalInteger;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.optionalString;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.redirect;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.requiredInteger;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.requiredString;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.models.Contact;
import dev.escalated.models.newsletter.Newsletter;
import dev.escalated.models.newsletter.NewsletterDelivery;
import dev.escalated.repositories.NewsletterDeliveryRepository;
import dev.escalated.repositories.NewsletterListMemberRepository;
import dev.escalated.repositories.NewsletterListRepository;
import dev.escalated.repositories.NewsletterRepository;
import dev.escalated.repositories.NewsletterTemplateRepository;
import dev.escalated.services.newsletter.NewsletterPermissionService;
import dev.escalated.services.newsletter.NewsletterPlanner;
import dev.escalated.services.newsletter.NewsletterRenderer;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/admin/newsletters")
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class AdminNewsletterController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EscalatedProperties properties;
    private final NewsletterPermissionService permissions;
    private final NewsletterPlanner planner;
    private final NewsletterRenderer renderer;
    private final NewsletterRepository newsletters;
    private final NewsletterListRepository lists;
    private final NewsletterTemplateRepository templates;
    private final NewsletterDeliveryRepository deliveries;
    private final NewsletterListMemberRepository members;
    private final JavaMailSender mailSender;

    public AdminNewsletterController(
            EscalatedProperties properties,
            NewsletterPermissionService permissions,
            NewsletterPlanner planner,
            NewsletterRenderer renderer,
            NewsletterRepository newsletters,
            NewsletterListRepository lists,
            NewsletterTemplateRepository templates,
            NewsletterDeliveryRepository deliveries,
            NewsletterListMemberRepository members,
            @Autowired(required = false) JavaMailSender mailSender) {
        this.properties = properties;
        this.permissions = permissions;
        this.planner = planner;
        this.renderer = renderer;
        this.newsletters = newsletters;
        this.lists = lists;
        this.templates = templates;
        this.deliveries = deliveries;
        this.members = members;
        this.mailSender = mailSender;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> index(
            Authentication auth, @RequestParam(defaultValue = "drafts") String tab) {
        permissions.require(auth, "newsletters.manage");
        List<String> statuses = switch (tab) {
            case "scheduled" -> List.of("scheduled", "sending", "paused");
            case "sent" -> List.of("sent", "failed");
            default -> List.of("draft");
        };
        List<Newsletter> rows = newsletters.findByStatusInOrderByCreatedAtDesc(statuses).stream()
                .limit(50)
                .toList();
        return ResponseEntity.ok(inertia("Escalated/Admin/Newsletters/Index", Map.of(
                "newsletters", rows,
                "tab", tab)));
    }

    @GetMapping("/new")
    public ResponseEntity<Map<String, Object>> create(Authentication auth) {
        permissions.require(auth, "newsletters.manage");
        return ResponseEntity.ok(inertia("Escalated/Admin/Newsletters/Compose", composeProps()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(Authentication auth, @RequestBody Map<String, Object> body) {
        permissions.require(auth, "newsletters.manage");
        NewsletterForm form = validateForm(body);
        if (Set.of("scheduled", "sending").contains(form.status())) {
            permissions.require(auth, "newsletters.send");
            if (!mailConfigured()) {
                return ResponseEntity.badRequest().body(Map.of("from_email", "Outbound mail is not configured."));
            }
        }
        Newsletter newsletter = new Newsletter();
        applyForm(newsletter, form);
        newsletter.setCreatedBy(userId(auth));
        newsletters.save(newsletter);
        if ("sending".equals(form.status())) {
            planner.plan(newsletter);
        }
        return ResponseEntity.ok(redirect("/admin/newsletters/" + newsletter.getId()));
    }

    @PostMapping("/preview")
    public ResponseEntity<Map<String, String>> preview(Authentication auth, @RequestBody Map<String, Object> body) {
        permissions.require(auth, "newsletters.manage");
        String fromEmail = assertEmail(optionalString(body, "from_email", 320), "from_email", false);
        if (fromEmail == null) {
            fromEmail = "preview@example.test";
        }
        Newsletter newsletter = previewNewsletter(body, fromEmail);
        Contact contact = previewContact();
        NewsletterDelivery delivery = previewDelivery(newsletter, contact, "preview");
        return ResponseEntity.ok(Map.of("html", renderer.render(delivery, newsletter, contact, null)));
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testSend(Authentication auth, @RequestBody Map<String, Object> body)
            throws Exception {
        permissions.require(auth, "newsletters.send");
        NewsletterForm form = validateForm(body);
        if (!mailConfigured()) {
            return ResponseEntity.badRequest().body(Map.of("from_email", "Outbound mail is not configured."));
        }
        Newsletter newsletter = previewNewsletter(body, form.fromEmail());
        Contact contact = testContact(auth, form);
        NewsletterDelivery delivery = previewDelivery(newsletter, contact, randomToken());
        delivery.setTest(true);
        String html = renderer.render(delivery, newsletter, contact, null);
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(contact.getEmail());
        helper.setFrom(formatFrom(form.fromEmail(), form.fromName()));
        helper.setSubject("[TEST] " + form.subject());
        helper.setText(html, true);
        mailSender.send(message);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/{newsletterId}")
    public ResponseEntity<Map<String, Object>> show(
            Authentication auth,
            @PathVariable Long newsletterId,
            @RequestParam(defaultValue = "overview") String tab,
            @RequestParam(required = false) String status) {
        permissions.require(auth, "newsletters.manage");
        Newsletter newsletter = findNewsletter(newsletterId);
        List<NewsletterDelivery> deliveryRows = status == null
                ? deliveries.findByNewsletterIdAndTestFalseOrderByIdDesc(newsletterId, PageRequest.of(0, 100))
                : deliveries.findByNewsletterIdAndTestFalseAndStatusOrderByIdDesc(
                        newsletterId, status, PageRequest.of(0, 100));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("newsletter", newsletter);
        props.put("deliveries", deliveryRows);
        props.put("topClicks", List.of());
        props.put("tab", tab);
        return ResponseEntity.ok(inertia("Escalated/Admin/Newsletters/Show", props));
    }

    @GetMapping("/{newsletterId}/edit")
    public ResponseEntity<Map<String, Object>> edit(Authentication auth, @PathVariable Long newsletterId) {
        permissions.require(auth, "newsletters.manage");
        Newsletter newsletter = findNewsletter(newsletterId);
        if (!Set.of("draft", "scheduled").contains(newsletter.getStatus())) {
            abort422("Only drafts and scheduled newsletters can be edited");
        }
        Map<String, Object> props = new LinkedHashMap<>(composeProps());
        props.put("newsletter", newsletter);
        return ResponseEntity.ok(inertia("Escalated/Admin/Newsletters/Edit", props));
    }

    @PutMapping("/{newsletterId}")
    public ResponseEntity<Map<String, Object>> update(
            Authentication auth, @PathVariable Long newsletterId, @RequestBody Map<String, Object> body) {
        permissions.require(auth, "newsletters.manage");
        Newsletter newsletter = findNewsletter(newsletterId);
        NewsletterForm form = validateForm(body);
        if (Set.of("scheduled", "sending").contains(form.status())) {
            permissions.require(auth, "newsletters.send");
        }
        applyForm(newsletter, form);
        newsletters.save(newsletter);
        if ("sending".equals(form.status())) {
            planner.plan(newsletter);
        }
        return ResponseEntity.ok(redirect("/admin/newsletters/" + newsletter.getId()));
    }

    @DeleteMapping("/{newsletterId}")
    public ResponseEntity<Map<String, Object>> destroy(Authentication auth, @PathVariable Long newsletterId) {
        permissions.require(auth, "newsletters.manage");
        Newsletter newsletter = findNewsletter(newsletterId);
        if (!"draft".equals(newsletter.getStatus())) {
            abort422("Only drafts can be deleted");
        }
        newsletters.delete(newsletter);
        return ResponseEntity.ok(redirect("/admin/newsletters"));
    }

    private NewsletterForm validateForm(Map<String, Object> body) {
        long targetListId = requiredInteger(body, "target_list_id", null, null);
        if (!lists.existsById(targetListId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "target_list_id does not exist");
        }
        Long templateId = optionalInteger(body, "template_id");
        if (templateId != null && !templates.existsById(templateId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "template_id does not exist");
        }
        return new NewsletterForm(
                requiredString(body, "subject", 998),
                assertEmail(requiredString(body, "from_email", 320), "from_email", true),
                optionalString(body, "from_name", 255),
                assertEmail(optionalString(body, "reply_to", 320), "reply_to", false),
                targetListId,
                templateId,
                optionalString(body, "theme", 64),
                optionalString(body, "body_markdown", null),
                assertOneOf(body.getOrDefault("status", "draft"), "status", Set.of("draft", "scheduled", "sending")),
                optionalDateAfterNow(body, "scheduled_at"));
    }

    private void applyForm(Newsletter newsletter, NewsletterForm form) {
        newsletter.setSubject(form.subject());
        newsletter.setFromEmail(form.fromEmail());
        newsletter.setFromName(form.fromName());
        newsletter.setReplyTo(form.replyTo());
        newsletter.setTargetListId(form.targetListId());
        newsletter.setTemplateId(form.templateId());
        newsletter.setTheme(form.theme());
        newsletter.setBodyMarkdown(form.bodyMarkdown());
        newsletter.setStatus(form.status());
        newsletter.setScheduledAt(form.scheduledAt());
    }

    private Map<String, Object> composeProps() {
        List<Map<String, Object>> listProps = lists.findAll().stream().map(list -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", list.getId());
            row.put("name", list.getName());
            row.put("member_count", members.countByListId(list.getId()));
            return row;
        }).toList();
        EscalatedProperties.NewslettersProperties config = properties.getNewsletters();
        Map<String, Object> props = new HashMap<>();
        props.put("lists", listProps);
        props.put("templates", templates.findAll().stream()
                .map(t -> Map.of("id", t.getId(), "name", t.getName()))
                .toList());
        props.put("themes", discoverThemes(Path.of(config.getThemesDir())));
        props.put("mailConfigured", mailConfigured());
        props.put("canSend", true);
        props.put("defaultFromEmail", config.getDefaultFrom());
        props.put("defaultReplyTo", config.getDefaultReplyTo());
        props.put("defaultTheme", config.getDefaultTheme());
        return props;
    }

    private Newsletter findNewsletter(Long id) {
        return newsletters.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Newsletter #" + id + " not found"));
    }

    private boolean mailConfigured() {
        return mailSender != null;
    }

    private static String userId(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    private static Newsletter previewNewsletter(Map<String, Object> body, String fromEmail) {
        Newsletter newsletter = new Newsletter();
        newsletter.setId(0L);
        String subject = optionalString(body, "subject", 998);
        newsletter.setSubject(subject == null ? "" : subject);
        newsletter.setFromEmail(fromEmail);
        newsletter.setTargetListId(optionalInteger(body, "target_list_id") == null ? 0L : optionalInteger(body, "target_list_id"));
        newsletter.setTheme(optionalString(body, "theme", 64) == null ? "default" : optionalString(body, "theme", 64));
        newsletter.setBodyMarkdown(optionalString(body, "body_markdown", null));
        newsletter.setStatus("draft");
        return newsletter;
    }

    private static Contact previewContact() {
        Contact contact = new Contact();
        contact.setId(0L);
        contact.setEmail("preview@example.test");
        contact.setName("Preview User");
        return contact;
    }

    private static Contact testContact(Authentication auth, NewsletterForm form) {
        Contact contact = new Contact();
        contact.setId(0L);
        contact.setEmail(auth == null ? form.fromEmail() : auth.getName());
        contact.setName("Tester");
        return contact;
    }

    private static NewsletterDelivery previewDelivery(Newsletter newsletter, Contact contact, String token) {
        NewsletterDelivery delivery = new NewsletterDelivery();
        delivery.setId(0L);
        delivery.setNewsletterId(newsletter.getId());
        delivery.setContactId(contact.getId());
        delivery.setEmailAtSend(contact.getEmail());
        delivery.setStatus("pending");
        delivery.setTrackingToken(token);
        return delivery;
    }

    private static String randomToken() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(40);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String formatFrom(String email, String name) throws Exception {
        if (name != null && !name.isBlank()) {
            return new InternetAddress(email, name).toString();
        }
        return email;
    }

    private record NewsletterForm(
            String subject,
            String fromEmail,
            String fromName,
            String replyTo,
            long targetListId,
            Long templateId,
            String theme,
            String bodyMarkdown,
            String status,
            java.time.Instant scheduledAt) {}
}
