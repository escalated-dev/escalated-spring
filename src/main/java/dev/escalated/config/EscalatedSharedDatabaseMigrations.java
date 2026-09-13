package dev.escalated.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Escalated's migrations on the host's database, while Escalated shares it.
 *
 * <p>A dedicated database is migrated as its {@code DataSource} is built, in
 * {@link EscalatedDedicatedPersistenceConfiguration}. The host's database is the
 * host's bean, so here the migrations run as a bean of their own that the
 * host's {@code EntityManagerFactory} — and the host's Flyway, if any — waits
 * for.
 *
 * <p>Every class here carries its conditions itself rather than inheriting them
 * from an enclosing configuration: Escalated component-scans this package, and
 * a scanned nested class is registered on its own conditions alone.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(EscalatedDatabaseCondition.OnSharedDatabase.class)
@ConditionalOnProperty(prefix = "escalated.datasource", name = "migrate", havingValue = "true", matchIfMissing = true)
public class EscalatedSharedDatabaseMigrations {

    /** Boot's default for {@code spring.flyway.table}. */
    private static final String HOST_HISTORY_TABLE = "flyway_schema_history";

    @Bean(name = EscalatedMigrationOrdering.MIGRATIONS_BEAN)
    EscalatedMigrations escalatedMigrations(DataSource dataSource, Environment environment) {
        return new EscalatedMigrations(dataSource, environment.getProperty("spring.flyway.table", HOST_HISTORY_TABLE));
    }

    @Bean
    static BeanFactoryPostProcessor escalatedMigrationsBeforeEntityManagerFactory() {
        return new EscalatedMigrationOrdering.BeforeEntityManagerFactory();
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(EscalatedDatabaseCondition.OnSharedDatabase.class)
    @ConditionalOnProperty(prefix = "escalated.datasource", name = "migrate", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = "org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer")
    static class BeforeTheHostsFlyway {

        @Bean
        static BeanFactoryPostProcessor escalatedMigrationsBeforeHostFlyway() {
            return new EscalatedMigrationOrdering.BeforeHostFlyway();
        }
    }
}
