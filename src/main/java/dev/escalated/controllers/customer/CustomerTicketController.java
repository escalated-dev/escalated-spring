package dev.escalated.controllers.customer;

import dev.escalated.dto.CustomerTicketDto;
import dev.escalated.dto.CustomerTicketSummaryDto;
import dev.escalated.dto.GuestReplyDto;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.services.TicketService;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The signed-in customer's own tickets.
 *
 * <p>The customer is the authenticated principal, whose name is their email
 * address (as everywhere else in the library), matched case-insensitively
 * against the ticket's requester email. Someone else's ticket answers 404, so
 * its existence is not revealed. An {@code email} query parameter or body
 * field naming someone else is ignored.
 *
 * <p>{@code show} and {@code store} answer with {@link CustomerTicketDto} and
 * {@code index} with {@link CustomerTicketSummaryDto}, never the ticket entity
 * or the agent detail view: public replies only, no internal notes, linked
 * tickets or other staff data. The reply endpoints answer with
 * {@link GuestReplyDto}: no author emails or mail headers.
 */
@RestController
@RequestMapping("/escalated/api/customer/tickets")
public class CustomerTicketController {

    private final TicketService ticketService;

    public CustomerTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ResponseEntity<Page<CustomerTicketSummaryDto>> index(Authentication authentication,
                                                                @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(ticketService.findCustomerTickets(customerEmail(authentication), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerTicketDto> show(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ticketService.findCustomerView(id, customerEmail(authentication)));
    }

    @PostMapping
    public ResponseEntity<CustomerTicketDto> store(@RequestBody Map<String, Object> body,
                                                   Authentication authentication) {
        String email = customerEmail(authentication);
        Object name = body.get("name");
        Ticket ticket = ticketService.create(
                (String) body.get("subject"),
                (String) body.get("body"),
                name instanceof String text && !text.isBlank() ? text : email,
                email,
                body.get("priority") != null ? TicketPriority.valueOf((String) body.get("priority")) : TicketPriority.MEDIUM,
                null);
        return ResponseEntity.status(201).body(ticketService.findCustomerView(ticket.getId(), email));
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<GuestReplyDto> addReply(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                  Authentication authentication) {
        Ticket ticket = ticketService.findRequestedBy(id, customerEmail(authentication));
        // Filed as the ticket's requester; any name or email in the body is ignored.
        Reply reply = ticketService.addReply(ticket.getId(),
                body.get("body"), ticket.getRequesterName(), ticket.getRequesterEmail(), "customer", false);
        return ResponseEntity.status(201).body(GuestReplyDto.from(reply));
    }

    @GetMapping("/{id}/replies")
    public ResponseEntity<List<GuestReplyDto>> replies(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ticketService.findCustomerReplies(id, customerEmail(authentication)));
    }

    private static String customerEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return authentication.getName().trim();
    }
}
