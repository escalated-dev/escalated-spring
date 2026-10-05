package dev.escalated.controllers.widget;

import dev.escalated.dto.GuestReplyDto;
import dev.escalated.dto.GuestTicketDto;
import dev.escalated.models.KnowledgeBaseArticle;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.models.TicketPriority;
import dev.escalated.services.KnowledgeBaseService;
import dev.escalated.services.SatisfactionRatingService;
import dev.escalated.services.TicketService;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalated/api/widget")
public class WidgetController {

    private final TicketService ticketService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SatisfactionRatingService ratingService;

    public WidgetController(TicketService ticketService,
                            KnowledgeBaseService knowledgeBaseService,
                            SatisfactionRatingService ratingService) {
        this.ticketService = ticketService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ratingService = ratingService;
    }

    @PostMapping("/tickets")
    @GuestThrottle(GuestThrottle.Scope.TICKET)
    public ResponseEntity<Ticket> createTicket(@RequestBody Map<String, String> body) {
        Ticket ticket = ticketService.create(
                body.get("subject"),
                body.get("body"),
                body.get("name"),
                body.get("email"),
                TicketPriority.MEDIUM,
                null);
        return ResponseEntity.status(201).body(ticket);
    }

    @GetMapping("/tickets/{token}")
    public ResponseEntity<GuestTicketDto> getTicketByToken(@PathVariable String token) {
        return ResponseEntity.ok(ticketService.findGuestView(token));
    }

    @PostMapping("/tickets/{token}/replies")
    // Counted before the token lookup, so wrong-token guesses count too.
    @GuestThrottle(GuestThrottle.Scope.REPLY)
    public ResponseEntity<GuestReplyDto> addReply(@PathVariable String token, @RequestBody Map<String, String> body) {
        Ticket ticket = ticketService.findByGuestToken(token);
        // The guest token belongs to the requester, so the reply is theirs;
        // any name or email in the body is ignored (as in the NestJS reference).
        Reply reply = ticketService.addReply(ticket.getId(),
                body.get("body"), ticket.getRequesterName(), ticket.getRequesterEmail(), "customer", false);
        return ResponseEntity.status(201).body(GuestReplyDto.from(reply));
    }

    @GetMapping("/tickets/{token}/replies")
    public ResponseEntity<List<GuestReplyDto>> getReplies(@PathVariable String token) {
        return ResponseEntity.ok(ticketService.findGuestReplies(token));
    }

    @GetMapping("/kb/search")
    public ResponseEntity<Page<KnowledgeBaseArticle>> searchKb(@RequestParam String query) {
        return ResponseEntity.ok(knowledgeBaseService.searchArticles(query, PageRequest.of(0, 10)));
    }

    @PostMapping("/csat/{token}")
    public ResponseEntity<Object> submitRating(@PathVariable String token,
                                               @RequestBody Map<String, Object> body) {
        int score = Integer.parseInt(body.get("rating").toString());
        String comment = (String) body.get("comment");
        return ResponseEntity.ok(ratingService.submitRating(token, score, comment));
    }
}
