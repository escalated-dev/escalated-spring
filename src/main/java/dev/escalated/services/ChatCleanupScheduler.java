package dev.escalated.services;

import dev.escalated.models.ChatSession;
import dev.escalated.repositories.ChatSessionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically ends chat sessions that have been idle for longer than
 * the configured timeout (30 minutes).
 */
@Component
public class ChatCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChatCleanupScheduler.class);
    private static final long IDLE_TIMEOUT_MINUTES = 30;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatSessionService chatSessionService;

    public ChatCleanupScheduler(ChatSessionRepository chatSessionRepository,
                                ChatSessionService chatSessionService) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatSessionService = chatSessionService;
    }

    @Scheduled(fixedRate = 60_000) // Every minute
    public void cleanupIdleSessions() {
        Instant cutoff = Instant.now().minus(IDLE_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        List<ChatSession> idleSessions = chatSessionRepository.findIdleSessions(cutoff);

        for (ChatSession session : idleSessions) {
            try {
                chatSessionService.end(session.getId());
                log.info("Ended idle chat session {} (ticket {})", session.getId(), session.getTicketId());
            } catch (Exception e) {
                log.warn("Failed to end idle chat session {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
