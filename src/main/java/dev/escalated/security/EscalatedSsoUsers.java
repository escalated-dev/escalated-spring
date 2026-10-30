package dev.escalated.security;

import org.springframework.security.core.Authentication;

/**
 * How single sign-on finds, and when allowed creates, the host's user for an identity provider's
 * email (ADR 2026-10-29-configurable-access-features). Escalated owns no users: a host replaces
 * the default by defining a bean of this type.
 *
 * <p>The default ({@link DefaultEscalatedSsoUsers}) uses the host's {@code UserDetailsService}
 * (case-insensitive by email), and creates users only through a {@code UserDetailsManager}, with an
 * unusable random password. Without a {@code UserDetailsService} it matches Escalated's agent
 * profiles by email and never creates anyone.
 */
public interface EscalatedSsoUsers {

    /** The signed-in user for {@code email}, or null when the host has no such user. */
    Authentication find(String email);

    /** Creates the host user and returns them signed in, or null when this host cannot create users. */
    Authentication create(String email, String name);
}
