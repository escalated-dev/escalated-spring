package dev.escalated.controllers.agent;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.ChatSession;
import dev.escalated.models.Reply;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.services.ChatSessionService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    private final AgentProfileRepository agents;

    public AgentChatController(ChatSessionService chatSessionService, AgentProfileRepository agents) {
        this.chatSessionService = chatSessionService;
        this.agents = agents;
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
                                              @RequestBody(required = false) Map<String, Long> body,
                                              Authentication authentication) {
        // Accepted by the signed-in agent. A body agentId is used only for a
        // host user with no agent profile (role-based staff).
        Long agentId = agents.findByEmail(authentication.getName())
                .map(AgentProfile::getId)
                .orElse(body == null ? null : body.get("agentId"));
        ChatSession session = chatSessionService.accept(sessionId, agentId);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<Reply> sendMessage(@PathVariable Long sessionId,
                                             @RequestBody Map<String, String> body,
                                             Authentication authentication) {
        // Sent as the signed-in agent; a name or email in the body is ignored.
        String email = authentication.getName();
        String name = agents.findByEmail(email)
                .map(AgentProfile::getName)
                .filter(n -> !n.isBlank())
                .orElse(email);
        Reply reply = chatSessionService.sendMessage(sessionId, body.get("body"), name, email, "agent");
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
