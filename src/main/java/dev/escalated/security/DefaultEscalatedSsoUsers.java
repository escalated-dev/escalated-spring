package dev.escalated.security;

import dev.escalated.models.AgentProfile;
import dev.escalated.repositories.AgentProfileRepository;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.UserDetailsManager;

/** The default {@link EscalatedSsoUsers}; see there. */
public class DefaultEscalatedSsoUsers implements EscalatedSsoUsers {

    private final ObjectProvider<UserDetailsService> users;
    private final AgentProfileRepository agents;
    private final SecureRandom random = new SecureRandom();

    public DefaultEscalatedSsoUsers(ObjectProvider<UserDetailsService> users, AgentProfileRepository agents) {
        this.users = users;
        this.agents = agents;
    }

    @Override
    public Authentication find(String email) {
        UserDetailsService service = users.getIfUnique();
        if (service != null) {
            for (String candidate : List.of(email, email.toLowerCase(Locale.ROOT))) {
                try {
                    UserDetails user = service.loadUserByUsername(candidate);
                    if (user != null && user.isEnabled() && user.isAccountNonLocked()) {
                        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
                    }
                } catch (UsernameNotFoundException unknown) {
                    // Try the next spelling.
                }
            }
            return null;
        }
        return agents.findAll().stream()
                .filter(profile -> profile.isActive() && profile.getEmail() != null
                        && profile.getEmail().equalsIgnoreCase(email))
                .findFirst()
                .map(DefaultEscalatedSsoUsers::signedIn)
                .orElse(null);
    }

    @Override
    public Authentication create(String email, String name) {
        if (!(users.getIfUnique() instanceof UserDetailsManager manager)) {
            return null;
        }
        byte[] password = new byte[32];
        random.nextBytes(password);
        manager.createUser(User.withUsername(email)
                .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(HexFormat.of().formatHex(password)))
                .roles("USER")
                .build());
        return find(email);
    }

    private static Authentication signedIn(AgentProfile profile) {
        return UsernamePasswordAuthenticationToken.authenticated(profile.getEmail(), null, List.of());
    }
}
