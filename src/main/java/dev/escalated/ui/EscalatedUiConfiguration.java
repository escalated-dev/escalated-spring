package dev.escalated.ui;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.plugins.PluginRegistry;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.security.EscalatedAuthorization;
import dev.escalated.services.SettingsService;
import jakarta.servlet.Filter;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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

    /**
     * The embeddable admin's settings (ADR 2026-11-05-embeddable-admin), with the host's
     * {@link EscalatedEmbedConfigurer}s applied. While embedding is on, the admin pages' route
     * names point under the embed prefix.
     */
    @Bean
    public EscalatedEmbed escalatedEmbed(EscalatedProperties properties, UiRoutes routes,
                                         ObjectProvider<EscalatedEmbedConfigurer> configurers) {
        EscalatedEmbed embed = new EscalatedEmbed(properties, configurers.orderedStream().toList());
        if (embed.enabled()) {
            routes.mountAdminAt(embed.basePath());
        }
        return embed;
    }

    /** The admin menu as data: {@code escalated.navigation}, and for server-rendered host layouts. */
    @Bean
    public AdminNavigation escalatedAdminNavigation(
            UiRoutes routes,
            EscalatedEmbed embed,
            EscalatedAuthorization authorization,
            dev.escalated.security.AccessFeatures accessFeatures,
            dev.escalated.security.AccessControl accessControl,
            ObjectProvider<PluginRegistry> plugins,
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mappings) {
        return new AdminNavigation(routes, embed, authorization, accessFeatures, accessControl,
                plugins::getIfAvailable, mappings::getIfAvailable);
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
            ObjectProvider<PluginRegistry> plugins,
            dev.escalated.security.AccessFeatures accessFeatures,
            dev.escalated.security.AccessControl accessControl,
            dev.escalated.services.TwoFactorService twoFactor,
            dev.escalated.services.AgentPanelService agentPanel,
            EscalatedEmbed embed,
            AdminNavigation navigation,
            UiRoutes routes) {
        TransactionTemplate readOnly = new TransactionTemplate(transactions);
        readOnly.setReadOnly(true);
        return new EscalatedSharedProps(authorization, agents, settings, properties, readOnly, plugins.getIfAvailable())
                .withAccess(accessFeatures, accessControl).withTwoFactor(twoFactor).withAgentPanel(agentPanel)
                .withEmbed(embed, navigation, routes);
    }

    @Bean
    @ConditionalOnMissingBean
    public Inertia escalatedInertia(
            EscalatedProperties properties,
            List<InertiaSharedProps> shared,
            InertiaRootTemplate template,
            UiRoutes routes,
            EscalatedEmbed embed,
            AdminNavigation navigation) {
        return new Inertia(properties, shared, template, routes).withEmbed(embed, navigation);
    }

    @Bean
    @ConditionalOnMissingBean
    public EscalatedUiRenderer escalatedUiRenderer(Inertia inertia) {
        return new InertiaUiRenderer(inertia);
    }

    @Bean
    public FilterRegistrationBean<InertiaFilter> escalatedInertiaFilter(EscalatedProperties properties,
                                                                       EscalatedEmbed embed) {
        FilterRegistrationBean<InertiaFilter> registration = new FilterRegistrationBean<>(new InertiaFilter(properties));
        registration.addUrlPatterns(UiRoutes.PREFIX + "/*");
        if (embed.enabled()) {
            registration.addUrlPatterns(embed.basePath(), embed.basePath() + "/*");
        }
        return registration;
    }

    /**
     * The outside of the embedded admin: the old admin URLs' 404, the embedded mark, redirects
     * under the prefix and iframe mode's framing headers. Ahead of every other filter. Only while
     * embedding may be on.
     */
    @Bean
    @Conditional(EmbedEnabledCondition.class)
    public FilterRegistrationBean<EmbedResponseFilter> escalatedEmbedResponseFilter(EscalatedEmbed embed) {
        FilterRegistrationBean<EmbedResponseFilter> registration =
                new FilterRegistrationBean<>(new EmbedResponseFilter(embed));
        registration.addUrlPatterns(EscalatedEmbed.ADMIN, EscalatedEmbed.ADMIN + "/*",
                embed.basePath(), embed.basePath() + "/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setEnabled(embed.enabled());
        return registration;
    }

    /**
     * Hands an embedded request, after the host's security chain and middleware, to Escalated's
     * admin at its standalone path, through Escalated's own security chain. Only while embedding
     * may be on.
     */
    @Bean
    @Conditional(EmbedEnabledCondition.class)
    public FilterRegistrationBean<EmbeddedAdminFilter> escalatedEmbeddedAdminFilter(
            EscalatedEmbed embed, ApplicationContext context) {
        EmbeddedAdminFilter filter = new EmbeddedAdminFilter(embed,
                () -> context.getBean(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME, Filter.class),
                name -> context.getBean(name, Filter.class));
        FilterRegistrationBean<EmbeddedAdminFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(embed.basePath(), embed.basePath() + "/*");
        // Just after Spring Security's filter (-100): the host's chain has run on the request as sent.
        registration.setOrder(-95);
        registration.setEnabled(embed.enabled());
        return registration;
    }

    /**
     * The Escalated/Error page for Inertia visits that end in an error. Ahead of Spring Security
     * (whose filter chain is at {@code -100}), so a refused gate or CSRF token is caught too.
     */
    @Bean
    public FilterRegistrationBean<InertiaErrorPageFilter> escalatedInertiaErrorPageFilter(
            EscalatedProperties properties, EscalatedEmbed embed) {
        FilterRegistrationBean<InertiaErrorPageFilter> registration =
                new FilterRegistrationBean<>(new InertiaErrorPageFilter(properties));
        registration.addUrlPatterns(UiRoutes.PREFIX, UiRoutes.PREFIX + "/*");
        if (embed.enabled()) {
            registration.addUrlPatterns(embed.basePath(), embed.basePath() + "/*");
        }
        registration.setOrder(-101);
        return registration;
    }
}
