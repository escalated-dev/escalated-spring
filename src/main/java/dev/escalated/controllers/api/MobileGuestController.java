package dev.escalated.controllers.api;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.controllers.widget.GuestThrottle;
import dev.escalated.controllers.widget.GuestVerificationController;
import dev.escalated.models.Department;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.services.DepartmentService;
import dev.escalated.services.GuestAccessGrants;
import dev.escalated.services.GuestAccessKeys;
import dev.escalated.services.GuestVerificationService;
import dev.escalated.services.MobileGuestTickets;
import dev.escalated.services.SettingsService;
import dev.escalated.services.TicketService;
import dev.escalated.services.ValidationFailedException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The mobile guest API (escalated-developer-context
 * {@code domain-model/mobile-guest-api.md}; escalated-laravel
 * {@code MobileGuestTicketController}). The same codes, grants and guest
 * budgets as the widget and guest routes; only the prefix and the payloads
 * differ. {@code {token}} is the grant itself, and any token that is not a
 * current ticket grant is a 404.
 *
 * <p>Every answer carries {@code Cache-Control: private, no-store} and
 * {@code Referrer-Policy: no-referrer} ({@link MobileGuestPrivacyFilter}).
 */
@RestController
@RequestMapping(MobileGuestController.PREFIX)
public class MobileGuestController {

    public static final String PREFIX = "/escalated/api/v1/mobile/guest";

    /** Grants are far shorter; anything longer is not one. */
    private static final int MAX_TOKEN_LENGTH = 1024;
    private static final int MAX_TEXT = 65_535;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final GuestVerificationController verificationEndpoints;
    private final GuestVerificationService verification;
    private final GuestAccessGrants grants;
    private final TicketService tickets;
    private final MobileGuestTickets presenter;
    private final DepartmentService departments;
    private final SettingsService settings;
    private final EscalatedProperties properties;

    public MobileGuestController(GuestVerificationController verificationEndpoints,
                                 GuestVerificationService verification, GuestAccessGrants grants,
                                 TicketService tickets, MobileGuestTickets presenter,
                                 DepartmentService departments, SettingsService settings,
                                 EscalatedProperties properties) {
        this.verificationEndpoints = verificationEndpoints;
        this.verification = verification;
        this.grants = grants;
        this.tickets = tickets;
        this.presenter = presenter;
        this.departments = departments;
        this.settings = settings;
        this.properties = properties;
    }

    /** The widget/guest {@code verification} handler, on the submissions budget. */
    @PostMapping("/verification")
    @GuestThrottle({GuestThrottle.Scope.REQUEST, GuestThrottle.Scope.TICKET})
    public ResponseEntity<?> verification(@RequestBody(required = false) Map<String, Object> body,
                                          HttpServletRequest request) {
        Object purpose = body == null ? null : body.get("purpose");
        if (("ticket".equals(purpose) || "lookup".equals(purpose)) && !guestTicketsEnabled()) {
            return guestTicketsDisabled();
        }
        if ("chat".equals(purpose) && !properties.getWidget().isEnabled()) {
            return ResponseEntity.status(403).body(Map.of("message", "Chat is not enabled."));
        }
        return verificationEndpoints.verification(body, request);
    }

    /** The widget/guest {@code lookup} handler, on the replies budget. */
    @PostMapping("/lookup")
    @GuestThrottle({GuestThrottle.Scope.REQUEST, GuestThrottle.Scope.REPLY})
    public ResponseEntity<?> lookup(@RequestBody(required = false) Map<String, Object> body) {
        if (!guestTicketsEnabled()) {
            return guestTicketsDisabled();
        }
        return verificationEndpoints.lookup(body);
    }

    /**
     * Files a ticket for a verified guest: guest tickets on (403), fields
     * (422), the {@code ticket} proof (422 on {@code verification_code},
     * nothing created), then the ticket on channel {@code web} and a grant.
     * Status, assignee, metadata and external reference cannot be set.
     */
    @PostMapping("/tickets")
    @GuestThrottle({GuestThrottle.Scope.REQUEST, GuestThrottle.Scope.TICKET})
    public ResponseEntity<?> create(@RequestBody(required = false) Map<String, Object> body) {
        if (!guestTicketsEnabled()) {
            return guestTicketsDisabled();
        }
        Map<String, Object> input = body == null ? Map.of() : body;
        Map<String, List<String>> errors = new LinkedHashMap<>();
        String name = required(input, "name", 255, errors);
        String email = email(input, errors);
        String subject = required(input, "subject", 255, errors);
        String description = required(input, "description", MAX_TEXT, errors);
        required(input, "verification_id", 64, errors);
        required(input, "verification_code", 16, errors);
        TicketPriority priority = priority(input.get("priority"), errors);
        Long departmentId = department(input.get("department_id"), errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        verification.claim(input.get("verification_id"), input.get("verification_code"), email, "ticket");
        Ticket ticket;
        GuestAccessGrants.Grant grant;
        try {
            ticket = tickets.create(subject, description, name, email, priority, departmentId, null, null, "web");
            grant = grants.issue(ticket.getId());
        } catch (RuntimeException ex) {
            verification.release(input.get("verification_id"));
            throw ex;
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("data", presenter.present(ticket.getId(), grant.token(), grant.expiresAt(), false));
        answer.put("message", "Ticket created.");
        return ResponseEntity.status(201).body(answer);
    }

    @GetMapping("/tickets/{token}")
    @GuestThrottle(GuestThrottle.Scope.REQUEST)
    public ResponseEntity<?> show(@PathVariable String token) {
        Optional<Ticket> ticket = resolve(token);
        if (ticket.isEmpty()) {
            return notFound();
        }
        return ResponseEntity.ok(Map.of("data", presenter.present(
                ticket.get().getId(), token, ticket.get().getGuestAccessExpiresAt(), true)));
    }

    /**
     * A public reply from the guest: grant (404), open ticket (422), fields
     * (422), then {@code email} must be the ticket's guest email (403).
     */
    @PostMapping("/tickets/{token}/replies")
    @GuestThrottle({GuestThrottle.Scope.REQUEST, GuestThrottle.Scope.REPLY})
    public ResponseEntity<?> reply(@PathVariable String token, @RequestBody(required = false) Map<String, Object> body) {
        Optional<Ticket> resolved = resolve(token);
        if (resolved.isEmpty()) {
            return notFound();
        }
        Ticket ticket = resolved.get();
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            return ResponseEntity.status(422).body(Map.of("message", "This ticket is closed."));
        }
        Map<String, Object> input = body == null ? Map.of() : body;
        Map<String, List<String>> errors = new LinkedHashMap<>();
        String text = required(input, "body", MAX_TEXT, errors);
        String email = email(input, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        if (!email.equals(GuestAccessKeys.normaliseEmail(ticket.getRequesterEmail()))) {
            return ResponseEntity.status(403).body(Map.of("message", "The provided email does not match this ticket."));
        }
        // The grant belongs to the requester, so the reply is theirs, as on the widget and guest routes.
        tickets.addReply(ticket.getId(), text, ticket.getRequesterName(), ticket.getRequesterEmail(), "customer", false);
        return ResponseEntity.status(201).body(Map.of("message", "Reply sent."));
    }

    private Optional<Ticket> resolve(String token) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        return grants.verify(token, null);
    }

    private boolean guestTicketsEnabled() {
        return settings.get("guest_tickets_enabled")
                .map(value -> "1".equals(value) || "true".equalsIgnoreCase(value))
                .orElse(true);
    }

    private static ResponseEntity<Map<String, String>> guestTicketsDisabled() {
        return ResponseEntity.status(403).body(Map.of("message", "Guest tickets are not enabled."));
    }

    private static ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(404).body(Map.of("message", "Not found."));
    }

    private static String required(Map<String, Object> input, String field, int max,
                                   Map<String, List<String>> errors) {
        Object value = input.get(field);
        if (value instanceof Number number) {
            value = number.toString();
        }
        if (!(value instanceof String text) || text.isBlank()) {
            errors.put(field, List.of("The " + field.replace('_', ' ') + " field is required."));
            return null;
        }
        if (text.length() > max) {
            errors.put(field, List.of("The " + field.replace('_', ' ') + " may not be greater than " + max
                    + " characters."));
            return null;
        }
        return text;
    }

    /** The normalised (trimmed, lower-cased) {@code email}, or an error. */
    private static String email(Map<String, Object> input, Map<String, List<String>> errors) {
        String raw = required(input, "email", 255, errors);
        if (raw == null) {
            return null;
        }
        String email = GuestAccessKeys.normaliseEmail(raw);
        if (!EMAIL.matcher(email).matches()) {
            errors.put("email", List.of("The email must be a valid email address."));
            return null;
        }
        return email;
    }

    private static TicketPriority priority(Object value, Map<String, List<String>> errors) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            return TicketPriority.MEDIUM;
        }
        if (value instanceof String text) {
            for (TicketPriority priority : TicketPriority.values()) {
                if (priority.name().toLowerCase(Locale.ROOT).equals(text.trim())) {
                    return priority;
                }
            }
        }
        errors.put("priority", List.of("The selected priority is invalid."));
        return null;
    }

    private Long department(Object value, Map<String, List<String>> errors) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            return null;
        }
        Long id = null;
        if (value instanceof Integer || value instanceof Long) {
            id = ((Number) value).longValue();
        } else if (value instanceof String text && text.trim().matches("\\d{1,18}")) {
            id = Long.parseLong(text.trim());
        }
        Department department = null;
        if (id != null) {
            try {
                department = departments.findById(id);
            } catch (EntityNotFoundException ex) {
                department = null;
            }
        }
        if (department == null || !department.isActive()) {
            errors.put("department_id", List.of("The selected department id is invalid."));
            return null;
        }
        return department.getId();
    }
}
