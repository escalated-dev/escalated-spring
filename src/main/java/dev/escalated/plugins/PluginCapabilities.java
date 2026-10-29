package dev.escalated.plugins;

import org.springframework.security.core.Authentication;

/**
 * Whether a user holds a capability that a plugin page, endpoint or UI item declares.
 *
 * <p>The default grants every capability to admins, and to anyone else the permissions of their
 * agent role (a capability is a permission name). A host replaces it by defining a bean of this
 * type; the first one, in {@code @Order}, is used.
 */
@FunctionalInterface
public interface PluginCapabilities {

    boolean allows(Authentication user, String capability);
}
