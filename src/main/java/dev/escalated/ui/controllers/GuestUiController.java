package dev.escalated.ui.controllers;

import dev.escalated.controllers.widget.GuestThrottle;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketStatus;
import dev.escalated.services.GuestAccessGrants;
import dev.escalated.services.GuestVerificationService;
import dev.escalated.services.SatisfactionRatingService;
import dev.escalated.services.TicketService;
import dev.escalated.ui.CustomerUiPages;
import dev.escalated.ui.EscalatedUiRenderer;
import dev.escalated.ui.GuestUiPages;
import dev.escalated.ui.Inertia;
import dev.escalated.ui.UiInput;
import dev.escalated.ui.UiRoutes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * The guest pages, as escalated-laravel's {@code routes/guest.php}: {@code Escalated/Guest/Create}
 * ({@code GET /escalated/guest/create}, {@code POST /escalated/guest}) and {@code Escalated/Guest/Show}
 * ({@code GET /escalated/guest/{token}}, with {@code reply} and {@code rate}). No sign-in: a
 * ticket is filed only with a verified email code, and opened only by its expiring guest grant
 * (or, while {@code escalated.guest-access.legacy-tokens} is on, a permanent token). An unknown,
 * expired or revoked link is a 404. Responses are not cached and send no referrer.
 */
@Controller
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
@RequestMapping(UiRoutes.PREFIX + "/guest")
public class GuestUiController {

    private static final String TOKEN = "/{token:[A-Za-z0-9_.-]{1,4096}}";

    private final EscalatedUiRenderer renderer;
    private final Inertia inertia;
    private final GuestUiPages pages;
    private final CustomerUiPages customerPages;
    private final GuestVerificationService verification;
    private final GuestAccessGrants grants;
    private final TicketService tickets;
    private final SatisfactionRatingService ratings;
    private final UiRoutes routes;

    public GuestUiController(EscalatedUiRenderer renderer, Inertia inertia, GuestUiPages pages,
                             CustomerUiPages customerPages, GuestVerificationService verification,
                             GuestAccessGrants grants, TicketService tickets, SatisfactionRatingService ratings,
                             UiRoutes routes) {
        this.renderer = renderer;
        this.inertia = inertia;
        this.pages = pages;
        this.customerPages = customerPages;
        this.verification = verification;
        this.grants = grants;
        this.tickets = tickets;
        this.ratings = ratings;
        this.routes = routes;
    }

    /** As Laravel's {@code GuestPrivacyHeaders}: a guest link must not leak through caches or referrers. */
    @ModelAttribute
    void privacyHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    @GetMapping("/create")
    public Object create() {
        if (!pages.enabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return renderer.render("Escalated/Guest/Create", pages.create());
    }

    @PostMapping({"", "/"})
    @GuestThrottle(GuestThrottle.Scope.TICKET)
    public ResponseEntity<Void> store(HttpServletRequest request) {
        if (!pages.enabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        refuseFiles(request);
        GuestUiPages.Submission submission = pages.validate(UiInput.of(request));
        // Mailbox proof first: spent with the ticket, handed back if the ticket cannot be filed.
        verification.claim(submission.verificationId(), submission.verificationCode(), submission.email(), "ticket");
        Ticket ticket;
        try {
            ticket = tickets.create(submission.subject(), submission.description(), submission.name(),
                    submission.email(), submission.priority(), submission.departmentId());
        } catch (RuntimeException failed) {
            verification.release(submission.verificationId());
            throw failed;
        }
        String token = grants.issue(ticket.getId()).token();
        inertia.flash("success", "Ticket created. Save this link to check your ticket status.");
        return inertia.redirect(routes.url("escalated.guest.tickets.show", token));
    }

    @GetMapping(TOKEN)
    public Object show(@PathVariable String token) {
        return renderer.render("Escalated/Guest/Show", pages.show(token));
    }

    @PostMapping(TOKEN + "/reply")
    @GuestThrottle(GuestThrottle.Scope.REPLY)
    public ResponseEntity<Void> reply(@PathVariable String token, HttpServletRequest request) {
        Ticket ticket = pages.resolve(token);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            return back(token, "error", "This ticket is closed.");
        }
        refuseFiles(request);
        String body = UiInput.of(request).requiredString("body", 65535);
        // The grant belongs to the requester, so the reply is theirs.
        tickets.addReply(ticket.getId(), body, ticket.getRequesterName(), ticket.getRequesterEmail(), "customer", false);
        return back(token, "success", "Reply sent.");
    }

    @PostMapping(TOKEN + "/rate")
    @GuestThrottle(GuestThrottle.Scope.REPLY)
    public ResponseEntity<Void> rate(@PathVariable String token, HttpServletRequest request) {
        Ticket ticket = pages.resolve(token);
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
            return back(token, "error", "Only resolved or closed tickets can be rated.");
        }
        if (customerPages.rated(ticket.getId())) {
            return back(token, "error", "This ticket has already been rated.");
        }
        ratings.rateTicket(ticket.getId(), (int) rating, comment);
        return back(token, "success", "Thank you for your feedback.");
    }

    private ResponseEntity<Void> back(String token, String kind, String message) {
        inertia.flash(kind, message);
        return inertia.redirect(routes.url("escalated.guest.tickets.show", token));
    }

    private static void refuseFiles(HttpServletRequest request) {
        if (request instanceof MultipartHttpServletRequest multipart && !multipart.getMultiFileMap().isEmpty()) {
            throw UiInput.invalid("attachments", "Attachments cannot be uploaded here yet.");
        }
    }
}
