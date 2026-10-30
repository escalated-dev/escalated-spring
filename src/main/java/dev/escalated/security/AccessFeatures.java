package dev.escalated.security;

import dev.escalated.config.EscalatedProperties;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The install-level access switches (ADR 2026-10-29-configurable-access-features).
 *
 * <p>{@code escalated.access.roles-permissions}, {@code escalated.access.two-factor} and
 * {@code escalated.access.sso}; unset, the environment variables {@code ESCALATED_ROLES_PERMISSIONS},
 * {@code ESCALATED_TWO_FACTOR} and {@code ESCALATED_SSO}; else off. They are read from configuration
 * only, never from the settings table: each changes who may sign in or what they may do, so no
 * stored setting may flip it.
 *
 * <p>A feature this port does not enforce yet is reported off whatever its switch says, so no screen
 * claims a protection that is not there.
 */
@Component
public class AccessFeatures {

    public static final String ROLES_PERMISSIONS = "roles_permissions";
    public static final String TWO_FACTOR = "two_factor";
    public static final String SSO = "sso";

    /** The features this port enforces. One not listed here is reported off. */
    static final Set<String> ENFORCED = Set.of(ROLES_PERMISSIONS, TWO_FACTOR);

    private final EscalatedProperties properties;
    private final Environment environment;

    public AccessFeatures(EscalatedProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public boolean rolesPermissions() {
        return enabled(ROLES_PERMISSIONS);
    }

    public boolean twoFactor() {
        return enabled(TWO_FACTOR);
    }

    public boolean sso() {
        return enabled(SSO);
    }

    /** Whether {@code feature} ({@code roles_permissions}, {@code two_factor} or {@code sso}) is on. */
    public boolean enabled(String feature) {
        if (!ENFORCED.contains(feature)) {
            return false;
        }
        EscalatedProperties.AccessProperties access = properties.getAccess();
        Boolean configured = switch (feature) {
            case ROLES_PERMISSIONS -> access.getRolesPermissions();
            case TWO_FACTOR -> access.getTwoFactor();
            case SSO -> access.getSso();
            default -> null;
        };
        if (configured != null) {
            return configured;
        }
        return truthy(environment.getProperty("ESCALATED_" + feature.toUpperCase(Locale.ROOT)));
    }

    /** The {@code escalated.features} entries the shared frontend reads. */
    public Map<String, Object> shared() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put(ROLES_PERMISSIONS, rolesPermissions());
        features.put(TWO_FACTOR, twoFactor());
        features.put(SSO, sso());
        return features;
    }

    static boolean truthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes")
                || normalized.equals("on");
    }
}
