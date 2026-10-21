package dev.escalated.ui.controllers;

import dev.escalated.models.Department;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.services.SatisfactionRatingService;
import dev.escalated.services.SettingsService;
import dev.escalated.services.TicketService;
import dev.escalated.ui.CustomerUiPages;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import dev.escalated.ui.UiTicketFilters;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * The customer pages: {@code Escalated/Customer/Index}, {@code Create} and {@code Show}, and their
 * writes, as escalated-laravel's {@code routes/customer.php}. Any signed-in user; each sees only
 * the tickets they requested (by email). Ticket paths take a reference ({@code ESC-000001}), so they
 * never shadow Escalated's other {@code /escalated/*} routes.
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX)
public class CustomerUiController {

    private static final String TICKET = "/{ticket:[A-Za-z0-9]+-[0-9]+}";
    private static final Set<String> TICKET_TYPES = Set.of("question", "problem", "incident", "task");

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final CustomerUiPages pages;
    private final TicketService tickets;
    private final DepartmentRepository departments;
    private final SettingsService settings;
    private final SatisfactionRatingService ratings;
    private final UiRoutes routes;

    public CustomerUiController(
            EscalatedUiRenderer renderer,
            Inertia inertia,
            CustomerUiPages pages,
            TicketService tickets,
            DepartmentRepository departments,
            SettingsService settings,
            SatisfactionRatingService ratings,
            UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.pages = pages;
        this.tickets = tickets;
        this.departments = departments;
        this.settings = settings;
        this.ratings = ratings;
        this.routes = routes;
    }

    @GetMapping({"", "/"})
    public Object index(@RequestParam Map<String, String> query, Authentication authentication) {
        return renderer.render("Escalated/Customer/Index", pages.index(email(authentication), query));
    }

    @GetMapping("/create")
    public Object create() {
        return renderer.render("Escalated/Customer/Create", pages.create());
    }

    @PostMapping({"", "/"})
    public ResponseEntity<Void> store(HttpServletRequest request, Authentication authentication) {
        refuseFiles(request);
        UiInput input = UiInput.of(request);
        String subject = input.requiredString("subject", 255);
        String description = input.requiredString("description", 65535);
        String givenPriority = input.string("priority");
        TicketPriority priority = givenPriority == null ? TicketPriority.MEDIUM : UiTicketFilters.parsePriority(givenPriority);
        if (priority == null) {
            throw UiInput.invalid("priority", "The selected priority is invalid.");
        }
        Long departmentId = input.longValue("department_id");
        if (departmentId != null && departments.findById(departmentId).filter(Department::isActive).isEmpty()) {
            throw UiInput.invalid("department_id", "The selected department id is invalid.");
        }
        String ticketType = input.string("ticket_type");
        if (ticketType != null && !TICKET_TYPES.contains(ticketType)) {
            throw UiInput.invalid("ticket_type", "The selected ticket type is invalid.");
        }
        String email = email(authentication);
        Ticket ticket = tickets.create(subject, description, email, email, priority, departmentId, ticketType);
        inertia.flash("success", "Ticket created.");
        return inertia.redirect(routes.url("escalated.customer.tickets.show", ticket.getTicketNumber()));
    }

    @GetMapping(TICKET)
    public Object show(@PathVariable("ticket") String reference, Authentication authentication) {
        return renderer.render("Escalated/Customer/Show", pages.show(reference, email(authentication)));
    }

    @PostMapping(TICKET + "/reply")
    public ResponseEntity<Void> reply(@PathVariable("ticket") String reference, HttpServletRequest request,
                                      Authentication authentication) {
        Ticket ticket = pages.own(reference, email(authentication));
        refuseFiles(request);
        String body = UiInput.of(request).requiredString("body", 65535);
        // Filed as the ticket's requester, whatever the body says.
        tickets.addReply(ticket.getId(), body, ticket.getRequesterName(), ticket.getRequesterEmail(), "customer", false);
        return done(ticket, "success", "Reply sent.");
    }

    @PostMapping(TICKET + "/close")
    public ResponseEntity<Void> close(@PathVariable("ticket") String reference, Authentication authentication) {
        Ticket ticket = pages.own(reference, email(authentication));
        if (!settings.get("allow_customer_close").map(v -> "1".equals(v) || "true".equalsIgnoreCase(v)).orElse(true)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customers cannot close tickets.");
        }
        tickets.changeStatus(ticket.getId(), TicketStatus.CLOSED, email(authentication));
        return done(ticket, "success", "Ticket closed.");
    }

    @PostMapping(TICKET + "/reopen")
    public ResponseEntity<Void> reopen(@PathVariable("ticket") String reference, Authentication authentication) {
        Ticket ticket = pages.own(reference, email(authentication));
        tickets.changeStatus(ticket.getId(), TicketStatus.OPEN, email(authentication));
        return done(ticket, "success", "Ticket reopened.");
    }

    @PostMapping(TICKET + "/rate")
    public ResponseEntity<Void> rate(@PathVariable("ticket") String reference, HttpServletRequest request,
                                     Authentication authentication) {
        Ticket ticket = pages.own(reference, email(authentication));
        UiInput input = UiInput.of(request);
        long rating = input.requiredLong("rating");
        if (rating < 1 || rating > 5) {
            throw UiInput.invalid("rating", "The rating field must be between 1 and 5.");
        }
        String comment = input.string("comment");
        if (comment != null && comment.length() > 2000) {
            throw UiInput.invalid("comment", "The comment field must not be greater than 2000 characters.");
        }
        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
            return done(ticket, "error", "Only resolved or closed tickets can be rated.");
        }
        if (pages.rated(ticket.getId())) {
            return done(ticket, "error", "This ticket has already been rated.");
        }
        ratings.rateTicket(ticket.getId(), (int) rating, comment);
        return done(ticket, "success", "Thank you for your feedback.");
    }

    private ResponseEntity<Void> done(Ticket ticket, String kind, String message) {
        inertia.flash(kind, message);
        return inertia.back(routes.url("escalated.customer.tickets.show", ticket.getTicketNumber()));
    }

    private static void refuseFiles(HttpServletRequest request) {
        if (request instanceof MultipartHttpServletRequest multipart && !multipart.getMultiFileMap().isEmpty()) {
            throw UiInput.invalid("attachments", "Attachments cannot be uploaded here yet.");
        }
    }

    private static String email(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return authentication.getName().trim();
    }
}
