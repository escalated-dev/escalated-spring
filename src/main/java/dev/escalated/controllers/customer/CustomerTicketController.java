package dev.escalated.controllers.customer;

import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.services.TicketService;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/customer/tickets")
public class CustomerTicketController {

    private final TicketService ticketService;

    public CustomerTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ResponseEntity<Page<Ticket>> index(@RequestParam String email,
                                              @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(ticketService.findByRequesterEmail(email, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ticket> show(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Ticket> store(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(ticketService.create(
                (String) body.get("subject"),
                (String) body.get("body"),
                (String) body.get("name"),
                (String) body.get("email"),
                body.get("priority") != null ? TicketPriority.valueOf((String) body.get("priority")) : TicketPriority.MEDIUM,
                null));
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<Reply> addReply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.status(201).body(ticketService.addReply(id,
                body.get("body"), body.get("name"), body.get("email"), "customer", false));
    }

    @GetMapping("/{id}/replies")
    public ResponseEntity<List<Reply>> replies(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getReplies(id));
    }
}
