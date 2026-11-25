package dev.escalated.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Whether the widget's routes are mounted: {@code escalated.widget.enabled} (environment
 * {@code ESCALATED_WIDGET_ENABLED}), true unless set otherwise. While it is false no
 * {@code /escalated/api/widget/**} route is registered at all: not the bundle, the config, ticket
 * creation, chat, the article search nor the verification and lookup endpoints.
 */
public class WidgetRoutesCondition extends SpringBootCondition {

    public static final String PROPERTY = "escalated.widget.enabled";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String value = context.getEnvironment().getProperty(PROPERTY);
        boolean enabled = RouteSwitches.isOn(value);
        return enabled
                ? ConditionOutcome.match(PROPERTY + " is on")
                : ConditionOutcome.noMatch(PROPERTY + " is " + value + ": the widget routes are not mounted");
    }
}
