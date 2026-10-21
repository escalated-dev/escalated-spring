package dev.escalated.security;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Who may use the admin and agent APIs.
 *
 * <p>The same two gates every Escalated host defines: an admin is a user with
 * {@code is_admin}; an agent is a user with {@code is_agent} or
 * {@code is_admin}. Escalated owns no user entity, so the flags live on
 * {@code escalated_agent_profiles} and the caller is matched to a profile by
 * principal name — the email address, which is also what an API token's
 * principal is and what the rest of the package already looks profiles up by.
 * A deactivated profile passes neither gate.
 *
 * <p>Hosts that keep roles in their own user store can grant
 * {@code ROLE_ESCALATED_ADMIN} or {@code ROLE_ESCALATED_AGENT} to their users
 * instead; either authority is honoured without a profile lookup.
 */
@Component
public class EscalatedAuthorization {

    /** Role name (without the {@code ROLE_} prefix) that grants the admin API. */
    public static final String ADMIN_ROLE = "ESCALATED_ADMIN";

    /** Role name (without the {@code ROLE_} prefix) that grants the agent API. */
    public static final String AGENT_ROLE = "ESCALATED_AGENT";

    static final String ADMIN_AUTHORITY = "ROLE_" + ADMIN_ROLE;
    static final String AGENT_AUTHORITY = "ROLE_" + AGENT_ROLE;

    /**
     * Carried by a request authenticated with an Escalated API token. Such a
     * request passes the gates only through the authorities the token's
     * abilities allow, never through a profile lookup by email.
     */
    public static final String API_TOKEN_AUTHORITY = "ESCALATED_API_TOKEN";

    /** The abilities a token may hold, as in Laravel: {@code agent}, {@code admin} or {@code *}. */
    public static final List<String> ABILITIES = List.of("agent", "admin", "*");

    private final AgentProfileRepository agents;

    public EscalatedAuthorization(AgentProfileRepository agents) {
        this.agents = agents;
    }

    /** Grants the request when the caller passes the admin gate. */
    public AuthorizationManager<RequestAuthorizationContext> admin() {
        return (authentication, context) -> new AuthorizationDecision(isAdmin(authentication.get()));
    }

    /** Grants the request when the caller passes the agent gate. */
    public AuthorizationManager<RequestAuthorizationContext> agent() {
        return (authentication, context) -> new AuthorizationDecision(isAgent(authentication.get()));
    }

    public boolean isAdmin(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return false;
        }
        if (hasAuthority(authentication, API_TOKEN_AUTHORITY)) {
            return hasAuthority(authentication, ADMIN_AUTHORITY);
        }
        if (hasAuthority(authentication, ADMIN_AUTHORITY)) {
            return true;
        }
        return profileOf(authentication)
                .map(profile -> profile.isActive() && profile.isAdmin())
                .orElse(false);
    }

    public boolean isAgent(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return false;
        }
        if (hasAuthority(authentication, API_TOKEN_AUTHORITY)) {
            // As in Laravel, the agent API needs the agent ability itself.
            return hasAuthority(authentication, AGENT_AUTHORITY);
        }
        if (hasAuthority(authentication, ADMIN_AUTHORITY) || hasAuthority(authentication, AGENT_AUTHORITY)) {
            return true;
        }
        return profileOf(authentication)
                .map(profile -> profile.isActive() && (profile.isAgent() || profile.isAdmin()))
                .orElse(false);
    }

    /**
     * The authorities an API token's principal carries, derived from its
     * agent profile at the moment the token is used — so a demotion takes
     * effect on the next request, not when the token is reissued.
     */
    public static List<GrantedAuthority> authoritiesOf(AgentProfile profile) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (profile == null || !profile.isActive()) {
            return authorities;
        }
        if (profile.isAgent() || profile.isAdmin()) {
            authorities.add(new SimpleGrantedAuthority(AGENT_AUTHORITY));
        }
        if (profile.isAdmin()) {
            authorities.add(new SimpleGrantedAuthority(ADMIN_AUTHORITY));
        }
        return authorities;
    }

    /**
     * The authorities an API token carries: its owner's gates, narrowed to the
     * token's abilities ({@code agent}, {@code admin}, {@code *}), plus the
     * API-token marker. Derived when the token is used, so a demotion or an
     * ability change takes effect on the next request.
     */
    public static List<GrantedAuthority> authoritiesOf(AgentProfile profile, String abilities) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(API_TOKEN_AUTHORITY));
        if (profile == null || !profile.isActive()) {
            return authorities;
        }
        java.util.Set<String> granted = abilitiesOf(abilities);
        boolean all = granted.contains("*");
        if ((profile.isAgent() || profile.isAdmin()) && (all || granted.contains("agent"))) {
            authorities.add(new SimpleGrantedAuthority(AGENT_AUTHORITY));
        }
        if (profile.isAdmin() && (all || granted.contains("admin"))) {
            authorities.add(new SimpleGrantedAuthority(ADMIN_AUTHORITY));
        }
        return authorities;
    }

    /**
     * The abilities stored on a token: a JSON array or a comma-separated list.
     * Blank (tokens made before abilities were enforced) means {@code *}.
     */
    public static java.util.Set<String> abilitiesOf(String stored) {
        java.util.Set<String> abilities = new java.util.LinkedHashSet<>();
        if (stored != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[A-Za-z*]+").matcher(stored);
            while (matcher.find()) {
                abilities.add(matcher.group().toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (abilities.isEmpty()) {
            abilities.add("*");
        }
        return abilities;
    }

    private Optional<AgentProfile> profileOf(Authentication authentication) {
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return agents.findByEmail(name);
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
