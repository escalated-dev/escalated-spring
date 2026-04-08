package dev.escalated.controllers.widget;

import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.services.TicketService;
import java.util.List;
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
    public ResponseEntity<Ticket> show(@PathVariable String token) {
        return ResponseEntity.ok(ticketService.findByGuestToken(token));
    }

    @GetMapping("/tickets/{token}/replies")
    public ResponseEntity<List<Reply>> replies(@PathVariable String token) {
        Ticket ticket = ticketService.findByGuestToken(token);
        return ResponseEntity.ok(ticketService.getReplies(ticket.getId()));
    }

    @PostMapping("/tickets/{token}/replies")
    public ResponseEntity<Reply> addReply(@PathVariable String token, @RequestBody Map<String, String> body) {
        Ticket ticket = ticketService.findByGuestToken(token);
        return ResponseEntity.status(201).body(ticketService.addReply(
                ticket.getId(), body.get("body"), body.get("name"), body.get("email"), "customer", false));
    }
}
