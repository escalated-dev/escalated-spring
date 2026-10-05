package dev.escalated.controllers.widget;

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
            return ResponseEntity.ok(ticketService.findByGuestToken(token));
        } catch (EntityNotFoundException ex) {
            return invalidToken();
        }
    }

    @GetMapping("/tickets/{token}/replies")
    public ResponseEntity<?> replies(@PathVariable String token) {
        Ticket ticket;
        try {
            ticket = ticketService.findByGuestToken(token);
        } catch (EntityNotFoundException ex) {
            return invalidToken();
        }
        return ResponseEntity.ok(ticketService.getReplies(ticket.getId()));
    }

    @PostMapping("/tickets/{token}/replies")
    public ResponseEntity<?> addReply(@PathVariable String token, @RequestBody Map<String, String> body) {
        Ticket ticket;
        try {
            ticket = ticketService.findByGuestToken(token);
        } catch (EntityNotFoundException ex) {
            return invalidToken();
        }
        return ResponseEntity.status(201).body(ticketService.addReply(
                ticket.getId(), body.get("body"), body.get("name"), body.get("email"), "customer", false));
    }

    /** Matches the NestJS reference's GuestAccessGuard. */
    private static ResponseEntity<Map<String, String>> invalidToken() {
        return ResponseEntity.status(403).body(Map.of("error", "Invalid guest access token"));
    }
}
