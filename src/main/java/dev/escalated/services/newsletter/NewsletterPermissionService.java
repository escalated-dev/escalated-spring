package dev.escalated.services.newsletter;

import dev.escalated.security.AccessControl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The newsletter screens' {@code newsletters.manage} / {@code newsletters.send} checks.
 *
 * <p>With roles and permissions off (the default) the admin gate in front of the newsletter routes is
 * the only authority, so any admin may use them. With it on, the user's role must grant the
 * permission (ADR 2026-10-29-configurable-access-features).
 */
@Service
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class NewsletterPermissionService {

    private final AccessControl access;

    public NewsletterPermissionService(AccessControl access) {
        this.access = access;
    }

    public void require(Authentication authentication, String permission) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authenticated");
        }
        if (!access.allows(authentication, permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }
}
