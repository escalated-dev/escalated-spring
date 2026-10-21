package dev.escalated.ui.controllers;

import dev.escalated.models.Ticket;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.services.MacroService;
import dev.escalated.services.TicketService;
import dev.escalated.ui.AdminTicketUiPages;
import dev.escalated.ui.AgentUiPages;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The admin ticket pages, {@code Escalated/Admin/Tickets/Index} and {@code Escalated/Admin/Tickets/Show},
 * as escalated-laravel's {@code Admin\TicketController}: the ticket writes the agent pages have
 * ({@link TicketWritesController}), plus {@code snooze}, {@code unsnooze} and the queue's
 * {@code bulk} action. Admins only (the web security chain).
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/admin")
public class AdminTicketUiController extends TicketWritesController {

    private final EscalatedUiRenderer renderer;
    private final AdminTicketUiPages adminPages;

    public AdminTicketUiController(
            EscalatedUiRenderer renderer,
            Inertia inertia,
            AgentUiPages pages,
            AdminTicketUiPages adminPages,
            TicketService tickets,
            MacroService macros,
            AgentProfileRepository agents,
            TagRepository tags,
            UiRoutes routes) {
        super(inertia, pages, tickets, macros, agents, tags, routes);
        this.renderer = renderer;
        this.adminPages = adminPages;
    }

    @Override
    protected String routePrefix() {
        return "escalated.admin";
    }

    @GetMapping("/tickets")
    public Object index(@RequestParam Map<String, String> filters, Authentication authentication) {
        return renderer.render("Escalated/Admin/Tickets/Index", adminPages.index(filters, agentId(authentication)));
    }

    @GetMapping("/tickets/{ticket}")
    public Object show(@PathVariable("ticket") String reference, Authentication authentication) {
        Ticket ticket = pages.resolve(reference);
        return renderer.render("Escalated/Admin/Tickets/Show", adminPages.show(ticket.getId(), agentId(authentication)));
    }

    @PostMapping("/tickets/bulk")
    public ResponseEntity<Void> bulk(HttpServletRequest request, Authentication authentication) {
        int changed = adminPages.bulk(UiInput.of(request), authentication.getName());
        inertia.flash("success", changed + " ticket(s) updated.");
        return inertia.back(routes.url("escalated.admin.tickets.index"));
    }

    @PostMapping("/tickets/{ticket}/snooze")
    public ResponseEntity<Void> snooze(@PathVariable("ticket") String reference, HttpServletRequest request,
                                       Authentication authentication) {
        Ticket ticket = pages.resolve(reference);
        Instant until = parseFuture(UiInput.of(request).string("snoozed_until"));
        tickets.snooze(ticket.getId(), until, authentication.getName());
        return done(ticket, "Ticket snoozed until " + until + ".");
    }

    @PostMapping("/tickets/{ticket}/unsnooze")
    public ResponseEntity<Void> unsnooze(@PathVariable("ticket") String reference, Authentication authentication) {
        Ticket ticket = pages.resolve(reference);
        tickets.unsnooze(ticket.getId(), authentication.getName());
        return done(ticket, "Ticket unsnoozed.");
    }

    /**
     * {@code snoozed_until} as SnoozeButton sends it (an ISO instant), or a local date-time in the
     * server's zone; it must be in the future.
     */
    static Instant parseFuture(String value) {
        if (value == null) {
            throw UiInput.invalid("snoozed_until", "The snoozed until field is required.");
        }
        Instant until;
        try {
            until = OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException notOffset) {
            try {
                until = LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException notADate) {
                throw UiInput.invalid("snoozed_until", "The snoozed until field must be a valid date.");
            }
        }
        if (!until.isAfter(Instant.now())) {
            throw UiInput.invalid("snoozed_until", "The snoozed until field must be a date after now.");
        }
        return until;
    }
}
