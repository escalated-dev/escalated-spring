package dev.escalated.controllers.widget;

import dev.escalated.models.ChatSession;
import dev.escalated.models.Reply;
import dev.escalated.services.ChatAvailabilityService;
import dev.escalated.services.ChatSessionService;
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
 */
@RestController
@RequestMapping("/escalated/api/widget/chat")
public class WidgetChatController {

    private final ChatSessionService chatSessionService;
    private final ChatAvailabilityService availabilityService;

    public WidgetChatController(ChatSessionService chatSessionService,
                                ChatAvailabilityService availabilityService) {
        this.chatSessionService = chatSessionService;
        this.availabilityService = availabilityService;
    }

    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> availability(@RequestParam(required = false) Long departmentId) {
        boolean available = availabilityService.isAvailable(departmentId);
        int queueDepth = availabilityService.getQueueDepth(departmentId);
        return ResponseEntity.ok(Map.of("available", available, "queueDepth", queueDepth));
    }

    @PostMapping("/start")
    public ResponseEntity<ChatSession> start(@RequestBody Map<String, String> body) {
        Long deptId = body.containsKey("departmentId") ? Long.parseLong(body.get("departmentId")) : null;
        ChatSession session = chatSessionService.start(
                body.getOrDefault("name", "Visitor"),
                body.get("email"),
                body.get("message"),
                deptId);
        return ResponseEntity.status(201).body(session);
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<Reply> sendMessage(@PathVariable Long sessionId,
                                             @RequestBody Map<String, String> body) {
        Reply reply = chatSessionService.sendMessage(
                sessionId, body.get("body"),
                body.getOrDefault("name", "Visitor"),
                body.getOrDefault("email", ""),
                "visitor");
        return ResponseEntity.status(201).body(reply);
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<ChatSession> end(@PathVariable Long sessionId) {
        return ResponseEntity.ok(chatSessionService.end(sessionId));
    }
}
