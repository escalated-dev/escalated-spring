package dev.escalated.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean;

/**
 * What has to wait for Escalated's migrations when they run on the host's
 * database.
 */
final class EscalatedMigrationOrdering {

    static final String MIGRATIONS_BEAN = "escalatedMigrations";

    private EscalatedMigrationOrdering() {
    }

    /**
     * The host's {@code EntityManagerFactory}. Under {@code ddl-auto=validate}
     * it checks Escalated's tables as it starts, so they have to exist first.
     */
    static final class BeforeEntityManagerFactory extends AbstractDependsOnBeanFactoryPostProcessor {

        BeforeEntityManagerFactory() {
            super(EntityManagerFactory.class, AbstractEntityManagerFactoryBean.class, MIGRATIONS_BEAN);
        }
    }

    /**
     * The host's own Flyway, when the host has Boot's Flyway support. An install
     * upgraded from a release that let the host's Flyway apply Escalated's
     * scripts still has their rows in the host's history; Escalated removes them
     * as it adopts them, and the host's Flyway must not validate before it has.
     */
    static final class BeforeHostFlyway extends AbstractDependsOnBeanFactoryPostProcessor {

        BeforeHostFlyway() {
            super(FlywayMigrationInitializer.class, MIGRATIONS_BEAN);
        }
    }
}
