package dev.escalated.controllers.widget;

import dev.escalated.dto.GuestReplyDto;
import dev.escalated.models.KnowledgeBaseArticle;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.services.DepartmentService;
import dev.escalated.services.GuestAccessGrants;
import dev.escalated.services.GuestVerificationService;
import dev.escalated.services.KnowledgeBaseService;
import dev.escalated.services.SatisfactionRatingService;
import dev.escalated.services.TicketService;
import dev.escalated.widget.WidgetMetadata;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/widget")
public class WidgetController {

    private final TicketService ticketService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SatisfactionRatingService ratingService;
    private final DepartmentService departmentService;
    private final GuestVerificationService verification;
    private final GuestAccessGrants grants;

    public WidgetController(TicketService ticketService,
                            KnowledgeBaseService knowledgeBaseService,
                            SatisfactionRatingService ratingService,
                            DepartmentService departmentService,
                            GuestVerificationService verification,
                            GuestAccessGrants grants) {
        this.ticketService = ticketService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ratingService = ratingService;
        this.departmentService = departmentService;
        this.verification = verification;
        this.grants = grants;
    }

    /**
     * The shared widget sends {@code description}; older clients send
     * {@code body}, which still works. It needs a {@code ticket} mailbox proof
     * ({@code verification_id}, {@code verification_code}). The answer is
     * {@link WidgetTicketCreated}: {@code message}, {@code reference}, and an
     * expiring grant as {@code guest_access_token} with {@code expires_at}.
     */
    @PostMapping("/tickets")
    @GuestThrottle(GuestThrottle.Scope.TICKET)
    public ResponseEntity<?> createTicket(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String subject = WidgetRequestBodies.text(body, "subject");
        String email = WidgetRequestBodies.text(body, "email");
        String description = WidgetRequestBodies.text(body, "description");
        if (description == null || description.isBlank()) {
            description = WidgetRequestBodies.text(body, "body");
        }
        String name = WidgetRequestBodies.text(body, "name");

        Map<String, List<String>> errors = new LinkedHashMap<>();
        if (subject == null || subject.isBlank()) {
            errors.put("subject", List.of("The subject is required."));
        }
        if (email == null || email.isBlank()) {
            errors.put("email", List.of("The email is required."));
        }
        if (description == null || description.isBlank()) {
            errors.put("description", List.of("The description is required."));
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.status(422).body(Map.of(
                    "message", errors.values().iterator().next().get(0),
                    "errors", errors));
        }

        Map<String, Object> metadata = WidgetMetadata.fromRequest(body.get("metadata"), request);
        // Mailbox proof first: the code is spent with the ticket, and handed
        // back if the ticket cannot be filed.
        verification.claim(body.get("verification_id"), body.get("verification_code"), email, "ticket");
        Ticket ticket;
        try {
            ticket = ticketService.create(
                    subject,
                    description,
                    name == null || name.isBlank() ? email : name,
                    email,
                    TicketPriority.MEDIUM,
                    WidgetRequestBodies.activeDepartment(body.get("department_id"), departmentService),
                    null,
                    metadata);
        } catch (RuntimeException ex) {
            verification.release(body.get("verification_id"));
            throw ex;
        }
        return GuestResponses.of(201, WidgetTicketCreated.of(ticket, grants.issue(ticket.getId())));
    }

    /**
     * With {@code Authorization: Bearer <grant>}, {@code reference} is the
     * ticket reference and the answer is what the Check Status tab reads.
     * Without one, while legacy tokens are on, {@code reference} is a
     * ticket's permanent guest token.
     */
    @GetMapping("/tickets/{reference}")
    public ResponseEntity<?> getTicket(@PathVariable String reference, HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        Optional<Ticket> ticket = grants.resolve(reference, authorization);
        if (ticket.isEmpty()) {
            return GuestResponses.invalidToken();
        }
        if (GuestAccessGrants.bearer(authorization) != null) {
            return GuestResponses.of(200, ticketService.guestStatus(ticket.get().getId()));
        }
        return GuestResponses.of(200, ticketService.guestView(ticket.get().getId()));
    }

    @PostMapping("/tickets/{reference}/replies")
    // Counted before the token lookup, so wrong-token guesses count too.
    @GuestThrottle(GuestThrottle.Scope.REPLY)
    public ResponseEntity<?> addReply(@PathVariable String reference, @RequestBody Map<String, String> body,
                                      HttpServletRequest request) {
        Optional<Ticket> resolved = grants.resolve(reference, request.getHeader("Authorization"));
        if (resolved.isEmpty()) {
            return GuestResponses.invalidToken();
        }
        Ticket ticket = resolved.get();
        // The grant belongs to the requester, so the reply is theirs; any name
        // or email in the body is ignored (as in the NestJS reference).
        Reply reply = ticketService.addReply(ticket.getId(),
                body.get("body"), ticket.getRequesterName(), ticket.getRequesterEmail(), "customer", false);
        return GuestResponses.of(201, GuestReplyDto.from(reply));
    }

    @GetMapping("/tickets/{reference}/replies")
    public ResponseEntity<?> getReplies(@PathVariable String reference, HttpServletRequest request) {
        Optional<Ticket> ticket = grants.resolve(reference, request.getHeader("Authorization"));
        if (ticket.isEmpty()) {
            return GuestResponses.invalidToken();
        }
        return GuestResponses.of(200, ticketService.guestReplies(ticket.get().getId()));
    }

    @GetMapping("/kb/search")
    public ResponseEntity<Page<KnowledgeBaseArticle>> searchKb(@RequestParam String query) {
        return ResponseEntity.ok(knowledgeBaseService.searchArticles(query, PageRequest.of(0, 10)));
    }

    /** Published articles matching {@code q} (at least 2 characters), as the widget reads them. */
    @GetMapping("/articles")
    public ResponseEntity<Map<String, Object>> articles(@RequestParam(name = "q", required = false) String q) {
        String query = q == null ? "" : q.trim();
        if (query.length() < 2) {
            return ResponseEntity.ok(Map.of("articles", List.of()));
        }
        List<Map<String, Object>> articles = knowledgeBaseService
                .searchArticles(query, PageRequest.of(0, 10))
                .map(article -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("title", article.getTitle());
                    entry.put("slug", article.getSlug());
                    entry.put("excerpt", article.getExcerpt());
                    return entry;
                })
                .getContent();
        return ResponseEntity.ok(Map.of("articles", articles));
    }

    /** One published article by slug; drafts and unknown slugs are 404. */
    @GetMapping("/articles/{slug}")
    public ResponseEntity<Map<String, Object>> article(@PathVariable String slug) {
        KnowledgeBaseArticle article = knowledgeBaseService.findPublishedArticle(slug);
        if (article == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Article not found."));
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("title", article.getTitle());
        answer.put("slug", article.getSlug());
        answer.put("body", article.getContent());
        answer.put("excerpt", article.getExcerpt());
        answer.put("category", article.getCategory() == null ? null : article.getCategory().getName());
        return ResponseEntity.ok(answer);
    }

    @PostMapping("/csat/{token}")
    public ResponseEntity<Object> submitRating(@PathVariable String token,
                                               @RequestBody Map<String, Object> body) {
        int score = Integer.parseInt(body.get("rating").toString());
        String comment = (String) body.get("comment");
        return ResponseEntity.ok(ratingService.submitRating(token, score, comment));
    }

}
