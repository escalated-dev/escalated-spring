package dev.escalated.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * A persistence unit of Escalated's own, active only when
 * {@code escalated.datasource.url} is set.
 *
 * <p>Without it Escalated's tables have to live in the host's database, because
 * its repositories bind to the host's {@code EntityManagerFactory}. With it they
 * bind here instead, and the host's database is left alone entirely — Escalated
 * creates no tables in it, runs no migrations against it and opens no
 * transactions on it.
 *
 * <p>The host's users stay in the host's database. Escalated owns no user
 * entity: the admin roles page reads {@code escalated_agent_profiles}, one of
 * Escalated's own tables, and ticket columns that reference a host user are
 * plain unconstrained values with no foreign key. That is deliberate — no
 * database can join across two connections.
 */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@Conditional(EscalatedDatabaseCondition.OnDedicatedDatabase.class)
@EnableConfigurationProperties(EscalatedDataSourceProperties.class)
public class EscalatedDedicatedPersistenceConfiguration {

    @Bean
    public DataSource escalatedDataSource(EscalatedDataSourceProperties properties) {
        DataSourceBuilder<?> builder = DataSourceBuilder.create().url(properties.getUrl());

        // Everything but the URL falls back to the host's own connection
        // details, so pointing Escalated at a second database on the same
        // server takes one line rather than five.
        if (properties.getUsername() != null) {
            builder.username(properties.getUsername());
        }
        if (properties.getPassword() != null) {
            builder.password(properties.getPassword());
        }
        if (properties.getDriverClassName() != null) {
            builder.driverClassName(properties.getDriverClassName());
        }

        DataSource dataSource = builder.build();

        // Migrate here rather than from a bean of its own, so that ordering is
        // a fact rather than a wiring detail: nothing can hold this DataSource
        // before its schema exists. A dedicated database starts empty and
        // nothing else is going to migrate it.
        if (properties.isMigrate()) {
            migrate(dataSource);
        }

        return dataSource;
    }

    /**
     * Runs Escalated's own migrations against Escalated's own database. The
     * host's Flyway keeps running against the host's database, untouched, and
     * the schema history tables are separate so neither sees the other's
     * versions.
     */
    private void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table("escalated_flyway_schema_history")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    @Bean(name = EscalatedPersistenceAliasRegistrar.ENTITY_MANAGER_FACTORY)
    public LocalContainerEntityManagerFactoryBean escalatedEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("escalatedDataSource") DataSource dataSource,
            EscalatedDataSourceProperties properties) {

        Map<String, Object> jpa = new HashMap<>();

        // Schema management belongs to Flyway. A stray create-drop here would
        // drop tables on a database the host may share with something else.
        jpa.put("hibernate.hbm2ddl.auto", properties.getDdlAuto());

        if (properties.getPlatform() != null) {
            jpa.put("hibernate.dialect", properties.getPlatform());
        }

        return builder
                .dataSource(dataSource)
                .packages("dev.escalated.models")
                .persistenceUnit("escalated")
                .properties(jpa)
                .build();
    }

    @Bean(name = EscalatedPersistenceAliasRegistrar.TRANSACTION_MANAGER)
    public PlatformTransactionManager escalatedTransactionManager(
            @Qualifier(EscalatedPersistenceAliasRegistrar.ENTITY_MANAGER_FACTORY)
            EntityManagerFactory entityManagerFactory) {

        return new JpaTransactionManager(entityManagerFactory);
    }

}
