package dev.escalated.controllers.widget;

import dev.escalated.models.Ticket;
import dev.escalated.services.GuestAccessGrants;
import dev.escalated.services.GuestVerificationService;
import dev.escalated.services.TicketService;
import dev.escalated.services.ValidationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mailbox proof and tracking lookup for guests, on the widget and the guest
 * prefixes (escalated-laravel {@code docs/guest-access.md}).
 */
@RestController
public class GuestVerificationController {

    private final GuestVerificationService verification;
    private final GuestAccessGrants grants;
    private final TicketService tickets;

    public GuestVerificationController(GuestVerificationService verification, GuestAccessGrants grants,
                                       TicketService tickets) {
        this.verification = verification;
        this.grants = grants;
        this.tickets = tickets;
    }

    /**
     * {@code {email, purpose}} where purpose is {@code ticket}, {@code chat}
     * or {@code lookup}. Answers 202 with the id; the code is only emailed.
     */
    @PostMapping({"/escalated/api/widget/verification", "/escalated/api/guest/verification"})
    @GuestThrottle(GuestThrottle.Scope.REPLY)
    public ResponseEntity<Map<String, Object>> verification(@RequestBody(required = false) Map<String, Object> body,
                                                            HttpServletRequest request) {
        Map<String, Object> input = body == null ? Map.of() : body;
        GuestVerificationService.Challenge challenge = verification.request(
                WidgetRequestBodies.text(input, "email"), WidgetRequestBodies.text(input, "purpose"),
                request.getRemoteAddr());
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("verification_id", challenge.verificationId());
        answer.put("expires_in", challenge.expiresIn());
        answer.put("message", "If the address can receive email, a verification code is on its way.");
        return GuestResponses.of(202, answer);
    }

    /**
     * {@code {email, reference, verification_id, verification_code}} with a
     * {@code lookup} proof. Answers {@code {data: [{reference, subject,
     * guest_access_token, expires_at}]}}: at most 20, possibly none. The proof
     * is spent either way.
     */
    @PostMapping({"/escalated/api/widget/lookup", "/escalated/api/guest/lookup"})
    @GuestThrottle(GuestThrottle.Scope.REPLY)
    public ResponseEntity<Map<String, Object>> lookup(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> input = body == null ? Map.of() : body;
        String email = WidgetRequestBodies.text(input, "email");
        String reference = WidgetRequestBodies.text(input, "reference");
        Map<String, List<String>> errors = new LinkedHashMap<>();
        if (email == null || email.isBlank()) {
            errors.put("email", List.of("The email field is required."));
        }
        if (reference == null || reference.isBlank()) {
            errors.put("reference", List.of("The reference field is required."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        verification.claim(input.get("verification_id"), input.get("verification_code"), email, "lookup");

        List<Map<String, Object>> matches = tickets.findForGuestLookup(reference, email).stream()
                .map(ticket -> match(ticket, grants.issue(ticket.getId())))
                .toList();
        return GuestResponses.of(200, Map.of("data", matches));
    }

    private static Map<String, Object> match(Ticket ticket, GuestAccessGrants.Grant grant) {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("reference", ticket.getTicketNumber());
        match.put("subject", ticket.getSubject());
        match.put("guest_access_token", grant.token());
        match.put("expires_at", grant.expiresAt().toString());
        return match;
    }
}
