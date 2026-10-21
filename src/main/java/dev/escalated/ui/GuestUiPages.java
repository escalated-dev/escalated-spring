package dev.escalated.ui;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.services.GuestAccessGrants;
import dev.escalated.services.SettingsService;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The guest pages' props, as escalated-laravel's {@code Guest\TicketController}: the submit form
 * (which verifies the email through the existing {@code /escalated/api/guest/verification} and
 * {@code /lookup}), and a ticket opened by its expiring guest grant. The ticket is the customer
 * page's payload ({@code CustomerTicketPayload::guestWeb}): public replies only, staff by display
 * name, nothing about the grant but when it expires.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class GuestUiPages {

    /** The priorities the form offers: Laravel's {@code escalated.priorities} this port stores. */
    public static final List<String> PRIORITIES = List.of("low", "medium", "high", "urgent");

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final TicketRepository tickets;
    private final DepartmentRepository departments;
    private final GuestAccessGrants grants;
    private final SettingsService settings;
    private final EscalatedProperties properties;
    private final AgentUiPages agentPages;

    public GuestUiPages(TicketRepository tickets, DepartmentRepository departments, GuestAccessGrants grants,
                        SettingsService settings, EscalatedProperties properties, AgentUiPages agentPages) {
        this.tickets = tickets;
        this.departments = departments;
        this.grants = grants;
        this.settings = settings;
        this.properties = properties;
        this.agentPages = agentPages;
    }

    /** The {@code guest_tickets_enabled} setting (default on); off, the submit form is a 404. */
    public boolean enabled() {
        return settings.get("guest_tickets_enabled").map(v -> "1".equals(v) || "true".equalsIgnoreCase(v)).orElse(true);
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> create() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("departments", agentPages.activeDepartments());
        props.put("priorities", PRIORITIES);
        props.put("verification_url", "/escalated/api/guest/verification");
        props.put("lookup_url", "/escalated/api/guest/lookup");
        return props;
    }

    /** What the form submitted, validated as Laravel's {@code Guest\TicketController::store}. */
    public record Submission(String name, String email, String subject, String description, TicketPriority priority,
                             Long departmentId, Object verificationId, Object verificationCode) {
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Submission validate(UiInput input) {
        String name = input.requiredString("guest_name", 255);
        String email = input.requiredString("guest_email", 255);
        if (!EMAIL.matcher(email).matches()) {
            throw UiInput.invalid("guest_email", "The guest email field must be a valid email address.");
        }
        String subject = input.requiredString("subject", 255);
        String description = input.requiredString("description", 65535);
        if (input.string("verification_id") == null) {
            throw UiInput.invalid("verification_id", "The verification id field is required.");
        }
        if (input.string("verification_code") == null) {
            throw UiInput.invalid("verification_code", "The verification code field is required.");
        }
        String given = input.string("priority");
        TicketPriority priority = TicketPriority.MEDIUM;
        if (given != null) {
            if (!PRIORITIES.contains(given.toLowerCase(Locale.ROOT))) {
                throw UiInput.invalid("priority", "The selected priority is invalid.");
            }
            priority = TicketPriority.valueOf(given.toUpperCase(Locale.ROOT));
        }
        Long departmentId = input.longValue("department_id");
        if (departmentId != null && departments.findById(departmentId).filter(d -> d.isActive()).isEmpty()) {
            throw UiInput.invalid("department_id", "The selected department id is invalid.");
        }
        return new Submission(name, email, subject, description, priority, departmentId,
                input.raw("verification_id"), input.raw("verification_code"));
    }

    /** The ticket a guest link opens: a current grant, or a permanent token while legacy tokens are on. */
    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Ticket resolve(String token) {
        Optional<Ticket> ticket = grants.verify(token, null);
        if (ticket.isEmpty() && properties.getGuestAccess().isLegacyTokens() && token != null
                && !token.startsWith(GuestAccessGrants.PREFIX)) {
            ticket = tickets.findByGuestAccessToken(token);
        }
        return ticket.orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
    }

    @Transactional(transactionManager = EscalatedTransactionManagers.ESCALATED, readOnly = true)
    public Map<String, Object> show(String token) {
        Ticket ticket = tickets.findById(resolve(token).getId())
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
        Map<String, Object> payload = CustomerUiPages.payload(ticket);
        // Downloads need a signed-in session; a guest sees the files listed, not linked.
        withoutUrls(payload.get("attachments"));
        if (payload.get("replies") instanceof List<?> replies) {
            for (Object reply : replies) {
                if (reply instanceof Map<?, ?> map) {
                    withoutUrls(map.get("attachments"));
                }
            }
        }
        payload.put("guest_access_expires_at", UiPresenter.iso(ticket.getGuestAccessExpiresAt()));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("ticket", payload);
        props.put("token", token);
        return props;
    }

    @SuppressWarnings("unchecked")
    private static void withoutUrls(Object attachments) {
        if (attachments instanceof List<?> list) {
            for (Object item : new ArrayList<>(list)) {
                if (item instanceof Map<?, ?> map) {
                    ((Map<String, Object>) map).put("url", null);
                }
            }
        }
    }
}
