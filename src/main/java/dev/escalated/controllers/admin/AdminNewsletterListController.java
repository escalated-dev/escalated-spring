package dev.escalated.controllers.admin;

import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.abort422;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.assertOneOf;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.filterJsonFromBody;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.inertia;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.optionalString;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.redirect;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.requiredInteger;
import static dev.escalated.controllers.newsletter.NewsletterHttpSupport.requiredString;

import dev.escalated.models.Contact;
import dev.escalated.models.newsletter.NewsletterList;
import dev.escalated.models.newsletter.NewsletterListMember;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.NewsletterListMemberRepository;
import dev.escalated.repositories.NewsletterListRepository;
import dev.escalated.services.newsletter.ContactSegmentResolver;
import dev.escalated.services.newsletter.NewsletterPermissionService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/escalated/api/admin/newsletters/lists")
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class AdminNewsletterListController {

    private final NewsletterPermissionService permissions;
    private final ContactSegmentResolver segments;
    private final NewsletterListRepository lists;
    private final NewsletterListMemberRepository members;
    private final ContactRepository contacts;

    public AdminNewsletterListController(
            NewsletterPermissionService permissions,
            ContactSegmentResolver segments,
            NewsletterListRepository lists,
            NewsletterListMemberRepository members,
            ContactRepository contacts) {
        this.permissions = permissions;
        this.segments = segments;
        this.lists = lists;
        this.members = members;
        this.contacts = contacts;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> index(Authentication auth) {
        permissions.require(auth, "newsletters.manage");
        List<Map<String, Object>> rows = lists.findAll().stream().map(this::withCounts).toList();
        return ResponseEntity.ok(inertia("Escalated/Admin/Newsletters/Lists/Index", Map.of("lists", rows)));
    }

    @GetMapping("/new")
    public ResponseEntity<Map<String, Object>> create(Authentication auth) {
        permissions.require(auth, "newsletters.manage");
        return ResponseEntity.ok(inertia("Escalated/Admin/Newsletters/Lists/Create", Map.of()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(Authentication auth, @RequestBody Map<String, Object> body) {
        permissions.require(auth, "newsletters.manage");
        NewsletterList list = new NewsletterList();
        list.setName(requiredString(body, "name", 255));
        list.setDescription(optionalString(body, "description", null));
        list.setKind(assertOneOf(body.get("kind"), "kind", Set.of("static", "dynamic")));
        list.setFilterJson(filterJsonFromBody(body, "filter_json"));
        list.setCreatedBy(userId(auth));
        lists.save(list);
        return ResponseEntity.ok(redirect("/admin/newsletters/lists/" + list.getId()));
    }

    @GetMapping("/{listId}")
    public ResponseEntity<Map<String, Object>> show(Authentication auth, @PathVariable Long listId) {
        permissions.require(auth, "newsletters.manage");
        NewsletterList list = findList(listId);
        List<NewsletterListMember> memberRows = members.findByListId(listId).stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .limit(100)
                .toList();
        int matchCount = "dynamic".equals(list.getKind())
                ? segments.countMatches(list.getFilterJson())
                : 0;
        return ResponseEntity.ok(inertia("Escalated/Admin/Newsletters/Lists/Show", Map.of(
                "list", withCounts(list),
                "members", memberRows,
                "matchCount", matchCount)));
    }

    @PutMapping("/{listId}")
    public ResponseEntity<Map<String, Object>> update(
            Authentication auth, @PathVariable Long listId, @RequestBody Map<String, Object> body) {
        permissions.require(auth, "newsletters.manage");
        NewsletterList list = findList(listId);
        if (body.containsKey("name")) {
            list.setName(requiredString(body, "name", 255));
        }
        if (body.containsKey("description")) {
            list.setDescription(optionalString(body, "description", null));
        }
        if (body.containsKey("filter_json")) {
            list.setFilterJson(filterJsonFromBody(body, "filter_json"));
        }
        lists.save(list);
        return ResponseEntity.ok(redirect("/admin/newsletters/lists/" + list.getId()));
    }

    @DeleteMapping("/{listId}")
    public ResponseEntity<Map<String, Object>> destroy(Authentication auth, @PathVariable Long listId) {
        permissions.require(auth, "newsletters.manage");
        lists.delete(findList(listId));
        return ResponseEntity.ok(redirect("/admin/newsletters/lists"));
    }

    @PostMapping("/{listId}/members")
    public ResponseEntity<Map<String, Object>> addMember(
            Authentication auth, @PathVariable Long listId, @RequestBody Map<String, Object> body) {
        permissions.require(auth, "newsletters.manage");
        NewsletterList list = findList(listId);
        if (!"static".equals(list.getKind())) {
            abort422("Members can only be added to static lists");
        }
        long contactId = requiredInteger(body, "contact_id", null, null);
        if (!contacts.existsById(contactId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "contact_id does not exist");
        }
        members.findByListIdAndContactId(listId, contactId).orElseGet(() -> {
            NewsletterListMember member = new NewsletterListMember();
            member.setListId(listId);
            member.setContactId(contactId);
            member.setAddedBy(userId(auth));
            member.setAddedAt(Instant.now());
            return members.save(member);
        });
        return ResponseEntity.ok(redirect("/admin/newsletters/lists/" + listId));
    }

    @DeleteMapping("/{listId}/members/{contactId}")
    public ResponseEntity<Map<String, Object>> removeMember(
            Authentication auth, @PathVariable Long listId, @PathVariable Long contactId) {
        permissions.require(auth, "newsletters.manage");
        NewsletterList list = findList(listId);
        if (!"static".equals(list.getKind())) {
            abort422("Members can only be removed from static lists");
        }
        members.deleteByListIdAndContactId(listId, contactId);
        return ResponseEntity.ok(redirect("/admin/newsletters/lists/" + listId));
    }

    @PostMapping("/{listId}/import")
    public ResponseEntity<Map<String, Object>> importCsv(
            Authentication auth, @PathVariable Long listId, @RequestParam("file") MultipartFile file)
            throws Exception {
        permissions.require(auth, "newsletters.manage");
        NewsletterList list = findList(listId);
        if (!"static".equals(list.getKind())) {
            abort422("CSV import is only supported for static lists");
        }
        int imported = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String email = line.split(",")[0].trim();
                if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    continue;
                }
                Contact contact = contacts.findByEmail(Contact.normalizeEmail(email)).orElseGet(() -> {
                    Contact created = new Contact();
                    created.setEmail(email);
                    return contacts.save(created);
                });
                if (members.findByListIdAndContactId(listId, contact.getId()).isEmpty()) {
                    NewsletterListMember member = new NewsletterListMember();
                    member.setListId(listId);
                    member.setContactId(contact.getId());
                    member.setAddedBy(userId(auth));
                    members.save(member);
                    imported++;
                }
            }
        }
        return ResponseEntity.ok(redirect("/admin/newsletters/lists/" + listId, Map.of("status", "Imported " + imported + " contacts")));
    }

    private Map<String, Object> withCounts(NewsletterList list) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", list.getId());
        row.put("name", list.getName());
        row.put("description", list.getDescription());
        row.put("kind", list.getKind());
        row.put("filter_json", list.getFilterJson());
        row.put("member_count", members.countByListId(list.getId()));
        row.put("opted_out_count", 0);
        return row;
    }

    private NewsletterList findList(Long id) {
        return lists.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "List #" + id + " not found"));
    }

    private static String userId(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    private static Map<String, Object> redirect(String url) {
        return redirect(url, Map.of());
    }

    private static Map<String, Object> redirect(String url, Map<String, Object> flash) {
        Map<String, Object> body = new LinkedHashMap<>(flash);
        body.put("redirect", url);
        return body;
    }
}
