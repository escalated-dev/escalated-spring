package dev.escalated.controllers.widget;

import dev.escalated.dto.GuestReplyDto;
import dev.escalated.models.Ticket;
import dev.escalated.services.GuestAccessGrants;
import dev.escalated.services.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The guest ticket routes. {@code {reference}} is the ticket reference with an
 * {@code Authorization: Bearer <grant>} (from {@code /lookup} or ticket
 * creation), or, while {@code escalated.guest-access.legacy-tokens} is on and
 * no bearer is sent, a ticket's permanent guest token.
 */
@RestController
@RequestMapping("/escalated/api/guest")
public class GuestAccessController {

    private final TicketService ticketService;
    private final GuestAccessGrants grants;

    public GuestAccessController(TicketService ticketService, GuestAccessGrants grants) {
        this.ticketService = ticketService;
        this.grants = grants;
    }

    @GetMapping("/tickets/{reference}")
    public ResponseEntity<?> show(@PathVariable String reference, HttpServletRequest request) {
        Optional<Ticket> ticket = grants.resolve(reference, request.getHeader("Authorization"));
        if (ticket.isEmpty()) {
            return GuestResponses.invalidToken();
        }
        return GuestResponses.of(200, ticketService.guestView(ticket.get().getId()));
    }

    @GetMapping("/tickets/{reference}/replies")
    public ResponseEntity<?> replies(@PathVariable String reference, HttpServletRequest request) {
        Optional<Ticket> ticket = grants.resolve(reference, request.getHeader("Authorization"));
        if (ticket.isEmpty()) {
            return GuestResponses.invalidToken();
        }
        return GuestResponses.of(200, ticketService.guestReplies(ticket.get().getId()));
    }

    @PostMapping("/tickets/{reference}/replies")
    // Shares the widget reply counter; counted before the token lookup.
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
        return GuestResponses.of(201, GuestReplyDto.from(ticketService.addReply(
                ticket.getId(), body.get("body"), ticket.getRequesterName(), ticket.getRequesterEmail(),
                "customer", false)));
    }
}
