package dev.escalated.services;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Surface enough of the host user table for an admin to grant or revoke
 * agent / admin access from the panel. The default Spring port pins this
 * to the {@code is_admin} / {@code is_agent} columns on
 * {@link AgentProfile} — hosts wiring authorisation differently (Spring
 * Security {@code GrantedAuthority}, a custom user table, etc.) should
 * override {@link dev.escalated.controllers.admin.AdminUserController}
 * in their own configuration.
 */
@Service
public class UserService {

    public static final String ERROR_CANNOT_SELF_DEMOTE = "You cannot remove your own admin role.";

    private final AgentProfileRepository agentRepository;

    public UserService(AgentProfileRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Transactional(readOnly = true)
    public Page<AgentProfile> search(String search, Pageable pageable) {
        String term = (search == null || search.isBlank())
                ? null
                : "%" + search.trim().toLowerCase() + "%";
        return agentRepository.searchOrderedByRole(term, pageable);
    }

    /**
     * Flip a single role on a target user. Returns the updated profile, or
     * throws {@link SelfDemoteException} if the caller tries to remove
     * their own admin role (which would lock them out of the panel they
     * are using).
     */
    @Transactional
    public AgentProfile updateRole(Long targetId, String role, boolean value, Long currentUserId) {
        if (!"admin".equals(role) && !"agent".equals(role)) {
            throw new IllegalArgumentException("Invalid role: " + role);
        }

        AgentProfile target = agentRepository.findById(targetId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + targetId));

        // Don't let an admin demote themselves and lock themselves out of
        // the admin panel they're trying to use.
        if ("admin".equals(role) && !value && currentUserId != null
                && currentUserId.equals(target.getId())) {
            throw new SelfDemoteException(ERROR_CANNOT_SELF_DEMOTE);
        }

        if ("admin".equals(role)) {
            target.setAdmin(value);
            // Admins are agents; flipping admin off does not also revoke
            // agent (an ex-admin can still answer tickets unless explicitly
            // demoted).
            if (value) {
                target.setAgent(true);
            }
        } else {
            target.setAgent(value);
            if (!value && target.isAdmin()) {
                // Revoking agent from an admin would leave the admin gate
                // on but the agent gate off — confusing. Demote fully.
                target.setAdmin(false);
            }
        }

        return agentRepository.save(target);
    }

    /** Thrown when an admin tries to remove their own admin role. */
    public static class SelfDemoteException extends RuntimeException {
        public SelfDemoteException(String message) {
            super(message);
        }
    }
}
