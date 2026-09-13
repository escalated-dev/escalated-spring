package dev.escalated.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@AutoConfiguration
@ConditionalOnProperty(prefix = "escalated", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({EscalatedProperties.class, EscalatedDataSourceProperties.class})
// Auto-configurations are excluded from the scan deliberately. They live in
// this package too, and a component scan registers them as ordinary
// configurations -- which runs them before auto-configuration ordering applies.
// EscalatedDedicatedPersistenceConfiguration defines a DataSource, and
// DataSourceAutoConfiguration backs off when one already exists, so scanning it
// suppressed the host's own DataSource entirely.
@ComponentScan(
        basePackages = "dev.escalated",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = AutoConfiguration.class))
@EnableJpaRepositories(
        basePackages = "dev.escalated.repositories",
        entityManagerFactoryRef = EscalatedPersistenceAliasRegistrar.ENTITY_MANAGER_FACTORY,
        transactionManagerRef = EscalatedPersistenceAliasRegistrar.TRANSACTION_MANAGER)
@EnableScheduling
// The webhook and workflow listeners are @Async, which is inert without this:
// they ran on the caller's thread, inside the caller's transaction.
@EnableAsync
@Import(MessageSourceConfig.class)
public class EscalatedAutoConfiguration {

    /**
     * Escalated's entities belong to the host's persistence unit only while
     * Escalated is sharing the host's database.
     *
     * <p>When {@code escalated.datasource.url} is set they belong to Escalated's
     * own persistence unit instead, and scanning them into the host's as well
     * would have Hibernate map every support table onto the host's database
     * too — validating, and under {@code ddl-auto} creating, tables the host
     * never asked for.
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(EscalatedDatabaseCondition.OnSharedDatabase.class)
    @EntityScan(basePackages = "dev.escalated.models")
    @Import(EscalatedPersistenceAliasRegistrar.class)
    public static class SharedPersistenceConfiguration {
    }
}
