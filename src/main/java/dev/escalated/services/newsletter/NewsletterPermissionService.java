package dev.escalated.services.newsletter;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class NewsletterPermissionService {

    private final AgentProfileRepository agentProfiles;

    public NewsletterPermissionService(AgentProfileRepository agentProfiles) {
        this.agentProfiles = agentProfiles;
    }

    @Transactional(readOnly = true)
    public void require(Authentication authentication, String permission) {
        AgentProfile profile = resolveProfile(authentication);
        if (profile.isAdmin()) {
            return;
        }
        if (profile.getRole() == null || !profile.getRole().hasPermission(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }

    private AgentProfile resolveProfile(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authenticated");
        }
        return agentProfiles.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No agent profile found"));
    }
}
