package dev.escalated.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Whether the guest routes are mounted: {@code escalated.guest.routes-enabled}, else the
 * {@code ESCALATED_GUEST_ROUTES} environment variable, else true (as escalated-laravel's
 * {@code escalated.guest.routes_enabled}).
 *
 * <p>While it is false no guest route is registered: not the guest pages under
 * {@code /escalated/guest}, nor {@code /escalated/api/guest/**} (the guest ticket API, verification
 * and lookup). The mobile guest API ({@code /escalated/api/v1/mobile/guest}) is not part of the
 * guest portal and is unaffected. With the routes mounted, the runtime {@code guest_tickets_enabled}
 * setting still switches guest tickets off as before.
 */
public class GuestRoutesCondition extends SpringBootCondition {

    public static final String PROPERTY = "escalated.guest.routes-enabled";
    public static final String ENV = "ESCALATED_GUEST_ROUTES";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return enabled(context.getEnvironment())
                ? ConditionOutcome.match(PROPERTY + " is on")
                : ConditionOutcome.noMatch(PROPERTY + " (" + ENV + ") is off: the guest routes are not mounted");
    }

    public static boolean enabled(Environment environment) {
        String value = environment.getProperty(PROPERTY);
        if (value == null || value.isBlank()) {
            value = environment.getProperty(ENV);
        }
        return RouteSwitches.isOn(value);
    }
}
