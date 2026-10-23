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

    /**
     * Transfer a chat to another agent and/or department (escalated-laravel
     * {@code escalated.admin.chat.transfer}). The body is
     * {@code {agent_id, department_id}} (either or both); the shared
     * ChatActionBar's {@code {type: "agent"|"department", id}} is read too.
     */
    @PostMapping("/{sessionId}/transfer")
    public ResponseEntity<Map<String, Object>> transfer(@PathVariable Long sessionId,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        return transfer(chatSessionService, sessionId, body);
    }

    /**
     * The transfer itself, shared with the agent UI route that TicketShow posts to from the
     * browser session. An unknown session throws {@code EntityNotFoundException} (404).
     */
    public static ResponseEntity<Map<String, Object>> transfer(ChatSessionService chatSessionService,
                                                               Long sessionId, Map<String, Object> body) {
        chatSessionService.findById(sessionId);
        Map<String, Object> in = body == null ? Map.of() : body;
        Object agent = in.get("agent_id");
        Object department = in.get("department_id");
        Object type = in.get("type");
        if ("agent".equals(type) && blank(agent)) {
            agent = in.get("id");
        } else if ("department".equals(type) && blank(department)) {
            department = in.get("id");
        }

        Long agentId;
        Long departmentId;
        try {
            agentId = id(agent, "agent_id");
            departmentId = id(department, "department_id");
        } catch (IllegalArgumentException invalid) {
            return unprocessable(invalid.getMessage());
        }
        if (agentId == null && departmentId == null) {
            return unprocessable("Provide an agent_id or department_id.");
        }

        try {
            ChatSession session = chatSessionService.transfer(sessionId, agentId, departmentId);
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("message", "Chat transferred.");
            response.put("session", session);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException refused) {
            return unprocessable(refused.getMessage());
        }
    }

    private static boolean blank(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }

    private static Long id(Object value, String field) {
        if (blank(value)) {
            return null;
        }
        if (value instanceof Number number && number.doubleValue() == number.longValue() && number.longValue() > 0) {
            return number.longValue();
        }
        if (value instanceof String text && text.trim().matches("[1-9][0-9]{0,17}")) {
            return Long.valueOf(text.trim());
        }
        throw new IllegalArgumentException("The " + field + " must be an integer.");
    }

    private static ResponseEntity<Map<String, Object>> unprocessable(String message) {
        return ResponseEntity.status(422).body(Map.of("message", message));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatSession> show(@PathVariable Long sessionId) {
        return ResponseEntity.ok(chatSessionService.findById(sessionId));
    }
}
