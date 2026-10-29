package dev.escalated.ui;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.plugins.PluginRegistry;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.security.EscalatedAuthorization;
import dev.escalated.services.SettingsService;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Inertia layer, present only with {@code escalated.ui.enabled=true}.
 *
 * <p>Each piece backs off when the host defines its own: {@link EscalatedUiRenderer},
 * {@link InertiaRootTemplate}, {@link UiRoutes} and {@link EscalatedSharedProps}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class EscalatedUiConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UiRoutes escalatedUiRoutes() {
        return new UiRoutes();
    }

    @Bean
    @ConditionalOnMissingBean
    public InertiaRootTemplate escalatedInertiaRootTemplate(EscalatedProperties properties, UiRoutes routes) {
        return new DefaultInertiaRootTemplate(properties, routes);
    }

    @Bean
    @ConditionalOnMissingBean
    public EscalatedSharedProps escalatedSharedProps(
            EscalatedAuthorization authorization,
            AgentProfileRepository agents,
            SettingsService settings,
            EscalatedProperties properties,
            @Qualifier(EscalatedTransactionManagers.ESCALATED) PlatformTransactionManager transactions,
            ObjectProvider<PluginRegistry> plugins) {
        TransactionTemplate readOnly = new TransactionTemplate(transactions);
        readOnly.setReadOnly(true);
        return new EscalatedSharedProps(authorization, agents, settings, properties, readOnly, plugins.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public Inertia escalatedInertia(
            EscalatedProperties properties,
            List<InertiaSharedProps> shared,
            InertiaRootTemplate template,
            UiRoutes routes) {
        return new Inertia(properties, shared, template, routes);
    }

    @Bean
    @ConditionalOnMissingBean
    public EscalatedUiRenderer escalatedUiRenderer(Inertia inertia) {
        return new InertiaUiRenderer(inertia);
    }

    @Bean
    public FilterRegistrationBean<InertiaFilter> escalatedInertiaFilter(EscalatedProperties properties) {
        FilterRegistrationBean<InertiaFilter> registration = new FilterRegistrationBean<>(new InertiaFilter(properties));
        registration.addUrlPatterns(UiRoutes.PREFIX + "/*");
        return registration;
    }
}
