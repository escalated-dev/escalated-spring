package dev.escalated.controllers.widget;

import dev.escalated.dto.GuestReplyDto;
import dev.escalated.dto.GuestTicketDto;
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
    public ResponseEntity<GuestTicketDto> show(@PathVariable String token) {
        return ResponseEntity.ok(ticketService.findGuestView(token));
    }

    @GetMapping("/tickets/{token}/replies")
    public ResponseEntity<List<GuestReplyDto>> replies(@PathVariable String token) {
        return ResponseEntity.ok(ticketService.findGuestReplies(token));
    }

    @PostMapping("/tickets/{token}/replies")
    public ResponseEntity<GuestReplyDto> addReply(@PathVariable String token, @RequestBody Map<String, String> body) {
        Ticket ticket = ticketService.findByGuestToken(token);
        return ResponseEntity.status(201).body(GuestReplyDto.from(ticketService.addReply(
                ticket.getId(), body.get("body"), body.get("name"), body.get("email"), "customer", false)));
    }
}
