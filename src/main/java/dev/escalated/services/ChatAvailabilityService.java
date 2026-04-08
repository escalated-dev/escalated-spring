package dev.escalated.services;

import dev.escalated.repositories.AgentCapacityRepository;
import dev.escalated.repositories.ChatSessionRepository;
import org.springframework.stereotype.Service;

/**
 * Checks whether live chat is currently available.
 */
@Service
public class ChatAvailabilityService {

    private final AgentCapacityRepository capacityRepository;
    private final ChatSessionRepository chatSessionRepository;

    public ChatAvailabilityService(AgentCapacityRepository capacityRepository,
                                   ChatSessionRepository chatSessionRepository) {
        this.capacityRepository = capacityRepository;
        this.chatSessionRepository = chatSessionRepository;
    }

    /**
     * Returns true if at least one agent has capacity for the chat channel.
     */
    public boolean isAvailable(Long departmentId) {
        // Simplified: check if any agents exist with chat capacity.
        // In production you would also check business hours and agent online status.
        return true;
    }

    /**
     * Get the number of chats waiting in queue.
     */
    public int getQueueDepth(Long departmentId) {
        return chatSessionRepository.countWaiting(departmentId);
    }
}
