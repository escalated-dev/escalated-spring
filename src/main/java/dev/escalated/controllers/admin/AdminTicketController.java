package dev.escalated.controllers.admin;

import dev.escalated.dto.TicketDetailDto;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.models.TicketStatus;
import dev.escalated.services.TicketService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/escalated/api/admin/tickets")
public class AdminTicketController {

    private final TicketService ticketService;

    public AdminTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ResponseEntity<Page<Ticket>> index(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(name = "department_id", required = false) Long departmentId,
            @RequestParam(name = "ticket_type", required = false) String ticketType,
            @RequestParam(name = "account_type", required = false) String accountType,
            @RequestParam(name = "account_id", required = false) String accountId,
            @PageableDefault(size = 25) Pageable pageable) {
        if (query != null
                || departmentId != null
                || ticketType != null
                || accountType != null
                || accountId != null) {
            return ResponseEntity.ok(
                    ticketService.search(
                            query,
                            null,
                            status,
                            departmentId,
                            ticketType,
                            accountType,
                            accountId,
                            pageable));
        }
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
        if (body.containsKey("ticket_type")) {
            return ResponseEntity.status(201)
                    .body(
                            ticketService.create(
                                    (String) body.get("subject"),
                                    (String) body.get("body"),
                                    (String) body.get("requester_name"),
                                    (String) body.get("requester_email"),
                                    body.get("priority") == null
                                            ? null
                                            : TicketPriority.valueOf((String) body.get("priority")),
                                    body.get("department_id") == null
                                            ? null
                                            : Long.valueOf(body.get("department_id").toString()),
                                    (String) body.get("ticket_type")));
        }
        Ticket ticket =
                ticketService.create(
                        (String) body.get("subject"),
                        (String) body.get("body"),
                        (String) body.get("requester_name"),
                        (String) body.get("requester_email"),
                        body.get("priority") != null
                                ? TicketPriority.valueOf((String) body.get("priority"))
                                : null,
                        body.get("department_id") != null
                                ? Long.valueOf(body.get("department_id").toString())
                                : null);
        return ResponseEntity.status(201).body(ticket);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ticket> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Ticket ticket =
                ticketService.update(
                        id,
                        (String) body.get("subject"),
                        (String) body.get("body"),
                        body.get("priority") != null
                                ? TicketPriority.valueOf((String) body.get("priority"))
                                : null);
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Ticket> assign(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                         Authentication authentication) {
        Long agentId =
                body.get("agent_id") != null ? Long.valueOf(body.get("agent_id").toString()) : null;
        return ResponseEntity.ok(
                ticketService.assign(id, agentId, actor(authentication)));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Ticket> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body,
                                               Authentication authentication) {
        return ResponseEntity.ok(ticketService.changeStatus(
                id, TicketStatus.valueOf(body.get("status")), actor(authentication)));
    }

    @PostMapping("/{id}/snooze")
    public ResponseEntity<Ticket> snooze(@PathVariable Long id, @RequestBody Map<String, String> body,
                                         Authentication authentication) {
        Instant until = Instant.parse(body.get("until"));
        return ResponseEntity.ok(ticketService.snooze(id, until, actor(authentication)));
    }

    @PostMapping("/{id}/merge")
    public ResponseEntity<Ticket> merge(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                        Authentication authentication) {
        Long targetId = Long.valueOf(body.get("target_ticket_id").toString());
        return ResponseEntity.ok(
                ticketService.merge(id, targetId, actor(authentication)));
    }

    @PostMapping("/{id}/split")
    public ResponseEntity<Ticket> split(@PathVariable Long id, @RequestBody Map<String, String> body,
                                        Authentication authentication) {
        return ResponseEntity.ok(ticketService.split(
                id, body.get("new_subject"), body.get("reply_ids"), actor(authentication)));
    }

    @PostMapping("/{id}/tags")
    public ResponseEntity<Ticket> addTag(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ticketService.addTag(id, body.get("tag")));
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    public ResponseEntity<Ticket> removeTag(@PathVariable Long id, @PathVariable Long tagId) {
        return ResponseEntity.ok(ticketService.removeTag(id, tagId));
    }

    /**
     * Moves the ticket to another department ({@code department_id}, or null
     * for none). Announced as {@code ticket.department_changed}.
     */
    @PostMapping("/{id}/department")
    public ResponseEntity<Ticket> changeDepartment(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                   Authentication authentication) {
        Object raw = body.get("department_id");
        Long departmentId = raw == null || raw.toString().isBlank() ? null : Long.valueOf(raw.toString());
        return ResponseEntity.ok(ticketService.changeDepartment(id, departmentId, actor(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> destroy(@PathVariable Long id, Authentication authentication) {
        ticketService.delete(id, actor(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * The actor recorded in activity and audit rows: the signed-in admin. An
     * {@code actor_email} in the request is ignored, so the log cannot be
     * written in someone else's name.
     */
    private static String actor(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
