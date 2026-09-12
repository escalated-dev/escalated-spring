package dev.escalated.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Whether the host gave Escalated a database of its own.
 *
 * <p>{@code @ConditionalOnProperty} cannot express this pair. With no
 * {@code havingValue} it matches any value except {@code false}, so it would
 * treat a blank {@code escalated.datasource.url=} as configured — and there is
 * no built-in way to spell the negation at all.
 */
abstract class EscalatedDatabaseCondition implements Condition {

    static final String URL_PROPERTY = "escalated.datasource.url";

    static boolean hasDedicatedDatabase(ConditionContext context) {
        String url = context.getEnvironment().getProperty(URL_PROPERTY);

        return url != null && !url.isBlank();
    }

    /** Matches when Escalated has a database of its own. */
    static final class OnDedicatedDatabase extends EscalatedDatabaseCondition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return hasDedicatedDatabase(context);
        }
    }

    /** Matches when Escalated shares the host's database — the default. */
    static final class OnSharedDatabase extends EscalatedDatabaseCondition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !hasDedicatedDatabase(context);
        }
    }
}
