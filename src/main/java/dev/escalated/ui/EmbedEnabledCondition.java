package dev.escalated.ui;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches while the admin may be embedded: {@code escalated.ui.embed.enabled=true}, or an
 * {@link EscalatedEmbedConfigurer} bean is defined. Without either, none of the embed beans exist,
 * so a default install's filters and routes are exactly what they were.
 */
class EmbedEnabledCondition implements ConfigurationCondition {

    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (Boolean.parseBoolean(context.getEnvironment().getProperty("escalated.ui.embed.enabled", "false"))) {
            return true;
        }
        ConfigurableListableBeanFactory beans = context.getBeanFactory();
        return beans != null && beans.getBeanNamesForType(EscalatedEmbedConfigurer.class, true, false).length > 0;
    }
}
