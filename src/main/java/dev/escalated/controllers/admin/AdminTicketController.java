package dev.escalated.controllers.admin;

import dev.escalated.dto.TicketDetailDto;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.services.TicketService;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/admin/tickets")
public class AdminTicketController {

    private final TicketService ticketService;

    public AdminTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ResponseEntity<Page<Ticket>> index(@RequestParam(required = false) TicketStatus status,
                                              @PageableDefault(size = 25) Pageable pageable) {
        Page<Ticket> tickets = status != null
                ? ticketService.findByStatus(status, pageable)
                : ticketService.findAll(pageable);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDetailDto> show(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.findByIdWithDetail(id));
    }

    @PostMapping
    public ResponseEntity<Ticket> store(@RequestBody Map<String, Object> body) {
        Ticket ticket = ticketService.create(
                (String) body.get("subject"),
                (String) body.get("body"),
                (String) body.get("requester_name"),
                (String) body.get("requester_email"),
                body.get("priority") != null ? TicketPriority.valueOf((String) body.get("priority")) : null,
                body.get("department_id") != null ? Long.valueOf(body.get("department_id").toString()) : null
        );
        return ResponseEntity.status(201).body(ticket);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ticket> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Ticket ticket = ticketService.update(id,
                (String) body.get("subject"),
                (String) body.get("body"),
                body.get("priority") != null ? TicketPriority.valueOf((String) body.get("priority")) : null
        );
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Ticket> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long agentId = body.get("agent_id") != null ? Long.valueOf(body.get("agent_id").toString()) : null;
        return ResponseEntity.ok(ticketService.assign(id, agentId, (String) body.get("actor_email")));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Ticket> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ticketService.changeStatus(
                id, TicketStatus.valueOf(body.get("status")), body.get("actor_email")));
    }

    @PostMapping("/{id}/snooze")
    public ResponseEntity<Ticket> snooze(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Instant until = Instant.parse(body.get("until"));
        return ResponseEntity.ok(ticketService.snooze(id, until, body.get("actor_email")));
    }

    @PostMapping("/{id}/merge")
    public ResponseEntity<Ticket> merge(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long targetId = Long.valueOf(body.get("target_ticket_id").toString());
        return ResponseEntity.ok(ticketService.merge(id, targetId, (String) body.get("actor_email")));
    }

    @PostMapping("/{id}/split")
    public ResponseEntity<Ticket> split(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ticketService.split(
                id, body.get("new_subject"), body.get("reply_ids"), body.get("actor_email")));
    }

    @PostMapping("/{id}/tags")
    public ResponseEntity<Ticket> addTag(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ticketService.addTag(id, body.get("tag")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> destroy(@PathVariable Long id,
                                        @RequestParam(defaultValue = "admin") String actorEmail) {
        ticketService.delete(id, actorEmail);
        return ResponseEntity.noContent().build();
    }
}
