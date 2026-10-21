package dev.escalated.controllers.widget;

import dev.escalated.models.ChatSession;
import dev.escalated.models.Reply;
import dev.escalated.models.Ticket;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.ChatAvailabilityService;
import dev.escalated.services.ChatSessionService;
import dev.escalated.services.DepartmentService;
import dev.escalated.services.GuestVerificationService;
import dev.escalated.services.ValidationFailedException;
import dev.escalated.services.SatisfactionRatingService;
import dev.escalated.services.TicketService;
import dev.escalated.widget.WidgetMetadata;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public widget endpoints for starting and participating in live chat sessions.
 *
 * <p>Every route after {@code /start} is keyed by the random token
 * {@code /start} returns as {@code id}. The numeric session id is never
 * accepted here: an unknown token, numeric ids included, is a 404.
 */
@RestController
@RequestMapping("/escalated/api/widget/chat")
public class WidgetChatController {

    private final ChatSessionService chatSessionService;
    private final ChatAvailabilityService availabilityService;
    private final TicketService ticketService;
    private final DepartmentService departmentService;
    private final SatisfactionRatingService ratingService;
    private final AgentProfileRepository agents;

    public WidgetChatController(ChatSessionService chatSessionService,
                                ChatAvailabilityService availabilityService,
                                TicketService ticketService,
                                DepartmentService departmentService,
                                SatisfactionRatingService ratingService,
                                AgentProfileRepository agents,
                                GuestVerificationService verification) {
        this.chatSessionService = chatSessionService;
        this.availabilityService = availabilityService;
        this.ticketService = ticketService;
        this.departmentService = departmentService;
        this.ratingService = ratingService;
        this.agents = agents;
        this.verification = verification;
    }

    private final GuestVerificationService verification;

    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> availability(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(name = "department_id", required = false) Long departmentIdSnake) {
        Long department = departmentId != null ? departmentId : departmentIdSnake;
        boolean available = availabilityService.isAvailable(department);
        int queueDepth = availabilityService.getQueueDepth(department);
        return ResponseEntity.ok(Map.of("available", available, "queueDepth", queueDepth));
    }

    @PostMapping("/start")
    // Every chat files a ticket, so starting one counts against the ticket limit.
    @GuestThrottle(GuestThrottle.Scope.TICKET)
    public ResponseEntity<Map<String, Object>> start(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        Object department = body.containsKey("department_id") ? body.get("department_id") : body.get("departmentId");
        Long deptId = WidgetRequestBodies.activeDepartment(department, departmentService);
        Map<String, Object> metadata = WidgetMetadata.fromRequest(body.get("metadata"), request);
        String name = WidgetRequestBodies.text(body, "name");
        String message = WidgetRequestBodies.text(body, "message");
        String email = WidgetRequestBodies.text(body, "email");
        if (email == null || email.isBlank()) {
            throw new ValidationFailedException(Map.of("email", List.of("The email field is required.")));
        }
        // Mailbox proof, spent with the chat and handed back if it cannot start.
        verification.claim(body.get("verification_id"), body.get("verification_code"), email, "chat");
        ChatSessionService.WidgetChat chat;
        try {
            chat = chatSessionService.startForWidget(
                    name == null || name.isBlank() ? "Visitor" : name,
                    email,
                    message,
                    deptId,
                    metadata);
        } catch (RuntimeException ex) {
            verification.release(body.get("verification_id"));
            throw ex;
        }
        ChatSession session = chat.session();

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("id", chat.token());
        answer.put("status", session.getStatus());
        answer.put("messages", message == null || message.isBlank()
                ? List.of()
                : List.of(initialMessage(session, message, session.getCreatedAt())));
        answer.put("agent", agentOf(session));
        answer.put("ticketId", session.getTicketId());
        answer.put("visitorName", session.getVisitorName());
        answer.put("visitorEmail", session.getVisitorEmail());
        answer.put("departmentId", session.getDepartmentId());
        answer.put("agentId", session.getAgentId());
        answer.put("acceptedAt", session.getAcceptedAt());
        answer.put("lastActivityAt", session.getLastActivityAt());
        answer.put("createdAt", session.getCreatedAt());
        return ResponseEntity.status(201).body(answer);
    }

    @GetMapping("/{token}/messages")
    public ResponseEntity<Map<String, Object>> messages(@PathVariable String token) {
        ChatSession session = chatSessionService.findByWidgetToken(token);
        Ticket ticket = ticketService.findById(session.getTicketId());

        List<Map<String, Object>> messages = new ArrayList<>();
        if (ticket.getBody() != null && !ticket.getBody().isBlank()) {
            messages.add(initialMessage(session, ticket.getBody(), ticket.getCreatedAt()));
        }
        for (Reply reply : ticketService.getReplies(session.getTicketId())) {
            if (reply.isInternal()) {
                continue;
            }
            boolean isAgent = "agent".equals(reply.getAuthorType());
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("id", reply.getId());
            message.put("body", reply.getBody());
            message.put("is_agent", isAgent);
            message.put("author", Map.of("name", isAgent ? reply.getAuthorName() : session.getVisitorName()));
            message.put("created_at", reply.getCreatedAt());
            messages.add(message);
        }

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("messages", messages);
        // Agents have no typing signal in this backend yet.
        answer.put("typing", null);
        answer.put("agent", agentOf(session));
        answer.put("ended", "ended".equals(session.getStatus()));
        answer.put("status", session.getStatus());
        return ResponseEntity.ok(answer);
    }

    @PostMapping("/{token}/messages")
    public ResponseEntity<?> sendMessage(@PathVariable String token, @RequestBody Map<String, Object> body) {
        ChatSession session = chatSessionService.findByWidgetToken(token);
        String text = WidgetRequestBodies.text(body, "body");
        if (text == null || text.isBlank()) {
            return WidgetRequestBodies.invalid("body", "The message is required.");
        }
        if ("ended".equals(session.getStatus())) {
            return WidgetRequestBodies.invalid("body", "This chat has ended.");
        }
        // The token belongs to the visitor, so the message is theirs; any
        // name or email in the body is ignored.
        Reply reply = chatSessionService.sendMessage(
                session.getId(), text,
                session.getVisitorName(),
                session.getVisitorEmail() != null ? session.getVisitorEmail() : "",
                "visitor");
        return ResponseEntity.status(201).body(reply);
    }

    @PostMapping("/{token}/typing")
    public ResponseEntity<Map<String, Object>> typing(@PathVariable String token) {
        chatSessionService.visitorTyping(chatSessionService.findByWidgetToken(token));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/{token}/end")
    public ResponseEntity<Map<String, Object>> end(@PathVariable String token) {
        ChatSession session = chatSessionService.findByWidgetToken(token);
        if (!"ended".equals(session.getStatus())) {
            session = chatSessionService.end(session.getId());
        }
        return ResponseEntity.ok(Map.of("ended", true, "status", session.getStatus()));
    }

    @PostMapping("/{token}/rate")
    public ResponseEntity<?> rate(@PathVariable String token, @RequestBody Map<String, Object> body) {
        ChatSession session = chatSessionService.findByWidgetToken(token);
        Integer rating = WidgetRequestBodies.integer(body.get("rating"));
        if (rating == null || rating < 1 || rating > 5) {
            return WidgetRequestBodies.invalid("rating", "The rating must be a whole number from 1 to 5.");
        }
        ratingService.rateTicket(session.getTicketId(), rating, WidgetRequestBodies.text(body, "comment"));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static Map<String, Object> initialMessage(ChatSession session, String body, Object createdAt) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", "initial");
        message.put("body", body);
        message.put("is_agent", false);
        message.put("author", Map.of("name", session.getVisitorName()));
        message.put("created_at", createdAt);
        return message;
    }

    private Map<String, Object> agentOf(ChatSession session) {
        if (session.getAgentId() == null) {
            return null;
        }
        return agents.findById(session.getAgentId())
                .map(agent -> Map.<String, Object>of("name", agent.getName()))
                .orElse(null);
    }
}
