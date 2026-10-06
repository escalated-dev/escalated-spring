package dev.escalated.controllers.widget;

import dev.escalated.dto.GuestReplyDto;
import dev.escalated.models.Ticket;
import dev.escalated.services.TicketService;
import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/guest")
public class GuestAccessController {

    private final TicketService ticketService;

    public GuestAccessController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/tickets/{token}")
    public ResponseEntity<?> show(@PathVariable String token) {
        try {
            return ResponseEntity.ok(ticketService.findGuestView(token));
        } catch (EntityNotFoundException ex) {
            return invalidToken();
        }
    }

    @GetMapping("/tickets/{token}/replies")
    public ResponseEntity<?> replies(@PathVariable String token) {
        try {
            return ResponseEntity.ok(ticketService.findGuestReplies(token));
        } catch (EntityNotFoundException ex) {
            return invalidToken();
        }
    }

    @PostMapping("/tickets/{token}/replies")
    // Shares the widget reply counter; counted before the token lookup.
    @GuestThrottle(GuestThrottle.Scope.REPLY)
    public ResponseEntity<?> addReply(@PathVariable String token, @RequestBody Map<String, String> body) {
        Ticket ticket;
        try {
            ticket = ticketService.findByGuestToken(token);
        } catch (EntityNotFoundException ex) {
            return invalidToken();
        }
        // The guest token belongs to the requester, so the reply is theirs;
        // any name or email in the body is ignored (as in the NestJS reference).
        return ResponseEntity.status(201).body(GuestReplyDto.from(ticketService.addReply(
                ticket.getId(), body.get("body"), ticket.getRequesterName(), ticket.getRequesterEmail(),
                "customer", false)));
    }

    /** Matches the NestJS reference's GuestAccessGuard. */
    private static ResponseEntity<Map<String, String>> invalidToken() {
        return ResponseEntity.status(403).body(Map.of("error", "Invalid guest access token"));
    }
}
