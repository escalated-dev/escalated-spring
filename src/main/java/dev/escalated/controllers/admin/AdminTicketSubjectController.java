package dev.escalated.controllers.admin;

import dev.escalated.dto.SerializedTicketSubjectDto;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketSubjectLink;
import dev.escalated.services.TicketService;
import dev.escalated.services.TicketSubjectService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/escalated/api/admin/tickets/{ticketId}/subjects")
public class AdminTicketSubjectController {

    private final TicketService ticketService;
    private final TicketSubjectService ticketSubjectService;

    public AdminTicketSubjectController(TicketService ticketService,
                                        TicketSubjectService ticketSubjectService) {
        this.ticketService = ticketService;
        this.ticketSubjectService = ticketSubjectService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> attach(@PathVariable Long ticketId,
                                                      @RequestBody Map<String, Object> body) {
        String type = requireString(body, "type");
        Object id = body.get("id");
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }
        if (!ticketSubjectService.isApiTypeAllowed(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Subject type [" + type + "] is not an allowed ticket subject.");
        }

        Ticket ticket = ticketService.findById(ticketId);
        String role = body.get("role") != null ? body.get("role").toString() : null;
        TicketSubjectLink link = ticketSubjectService.attach(ticket, type, id, role);
        List<SerializedTicketSubjectDto> subjects = ticketSubjectService.serializeLinks(List.of(link));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("link", link);
        response.put("subjects", subjects);
        return ResponseEntity.status(201).body(response);
    }

    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> detach(@PathVariable Long ticketId, @PathVariable Long linkId) {
        Ticket ticket = ticketService.findById(ticketId);
        ticketSubjectService.detach(ticket, linkId);
        return ResponseEntity.noContent().build();
    }

    private static String requireString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return value.toString();
    }
}
