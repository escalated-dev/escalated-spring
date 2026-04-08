package dev.escalated.controllers.agent;

import dev.escalated.models.ChatSession;
import dev.escalated.models.Reply;
import dev.escalated.services.ChatSessionService;
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
 * Agent-facing endpoints for managing live chat sessions.
 */
@RestController
@RequestMapping("/escalated/api/agent/chat")
public class AgentChatController {

    private final ChatSessionService chatSessionService;

    public AgentChatController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @GetMapping("/queue")
    public ResponseEntity<List<ChatSession>> queue() {
        return ResponseEntity.ok(chatSessionService.getWaitingSessions());
    }

    @GetMapping("/active")
    public ResponseEntity<List<ChatSession>> activeSessions(@RequestParam Long agentId) {
        return ResponseEntity.ok(chatSessionService.getActiveSessionsForAgent(agentId));
    }

    @PostMapping("/{sessionId}/accept")
    public ResponseEntity<ChatSession> accept(@PathVariable Long sessionId,
                                              @RequestBody Map<String, Long> body) {
        ChatSession session = chatSessionService.accept(sessionId, body.get("agentId"));
        return ResponseEntity.ok(session);
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<Reply> sendMessage(@PathVariable Long sessionId,
                                             @RequestBody Map<String, String> body) {
        Reply reply = chatSessionService.sendMessage(
                sessionId, body.get("body"), body.get("name"), body.get("email"), "agent");
        return ResponseEntity.status(201).body(reply);
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<ChatSession> end(@PathVariable Long sessionId) {
        return ResponseEntity.ok(chatSessionService.end(sessionId));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatSession> show(@PathVariable Long sessionId) {
        return ResponseEntity.ok(chatSessionService.findById(sessionId));
    }
}
