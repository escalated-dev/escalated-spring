package dev.escalated.security;

import dev.escalated.repositories.AgentProfileRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * What a signed-in user may do under roles &amp; permissions (ADR
 * 2026-10-29-configurable-access-features).
 *
 * <p>A user's permissions are those of their agent profile's role, matched by principal name (the
 * email address, which is also an API token's principal: a token acts as the user who owns it).
 * Nothing else grants a permission: not the admin gate, not {@code is_admin}, not a host's
 * {@code ROLE_ESCALATED_ADMIN}. A user who should have everything holds the {@code admin} role.
 */
@Component
public class AccessControl {

    private final AccessFeatures features;
    private final AgentProfileRepository agents;

    public AccessControl(AccessFeatures features, AgentProfileRepository agents) {
        this.features = features;
        this.agents = agents;
    }

    public boolean enforced() {
        return features.rolesPermissions();
    }

    /** The permission slugs {@code user}'s role grants. Empty for anyone without a role. */
    public Set<String> permissionsOf(Authentication user) {
        if (!isSignedIn(user) || user.getName() == null || user.getName().isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(agents.findPermissionNamesByEmail(user.getName()));
    }

    /**
     * Whether {@code user} holds every one of {@code slugs}. Always true while roles and permissions
     * is off: the admin and agent gates are then the only authority.
     */
    public boolean allows(Authentication user, Collection<String> slugs) {
        if (!enforced()) {
            return true;
        }
        if (slugs.isEmpty()) {
            return true;
        }
        return permissionsOf(user).containsAll(slugs);
    }

    public boolean allows(Authentication user, String slug) {
        return allows(user, List.of(slug));
    }

    /**
     * The {@code escalated.permissions} shared prop: with the switch on, the user's slugs; off,
     * {@code ['*']} for staff (the gates decide everything) and nothing for anyone else.
     */
    public List<String> shared(Authentication user, boolean staff) {
        if (!enforced()) {
            return staff ? List.of("*") : List.of();
        }
        return List.copyOf(permissionsOf(user));
    }

    private static boolean isSignedIn(Authentication user) {
        return user != null && user.isAuthenticated() && !(user instanceof AnonymousAuthenticationToken);
    }
}
