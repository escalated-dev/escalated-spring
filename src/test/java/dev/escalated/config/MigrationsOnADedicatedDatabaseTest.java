package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;

import hostapp.HostApplication;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * The same migrations on a database of Escalated's own.
 *
 * <p>The host stays on H2, as in {@link DatabaseConnectionTest}; Escalated gets
 * an empty PostgreSQL or MySQL database and validates its entities against what
 * the migrations built there.
 */
@SpringBootTest(classes = HostApplication.class)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
@DirtiesContext
@TestPropertySource(
        properties = {
            "spring.mail.host=localhost",
            "spring.datasource.url=jdbc:h2:mem:hostdb_dedicated_migrations;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "escalated.datasource.ddl-auto=validate",
            "escalated.datasource.migrate=true",
        })
class MigrationsOnADedicatedDatabaseTest {

    private static ScratchDatabase database;

    @DynamicPropertySource
    static void emptyDatabase(DynamicPropertyRegistry registry) {
        database = ScratchDatabase.create("dedicated");
        registry.add("escalated.datasource.url", database::url);
        registry.add("escalated.datasource.username", ScratchDatabase::username);
        registry.add("escalated.datasource.password", ScratchDatabase::password);
        registry.add("escalated.datasource.driver-class-name", () -> System.getenv("ESCALATED_TEST_DRIVER_CLASS"));
        registry.add("escalated.datasource.platform", () -> System.getenv("ESCALATED_TEST_PLATFORM"));
    }

    @AfterAll
    static void dropDatabase() {
        if (database != null) {
            database.drop();
        }
    }

    @Autowired
    @Qualifier("escalatedDataSource")
    private DataSource escalatedDataSource;

    @Test
    void everyMigrationIsRecordedInEscalatedsOwnHistory() {
        Integer applied = new JdbcTemplate(escalatedDataSource).queryForObject(
                "SELECT COUNT(*) FROM escalated_flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                Integer.class);

        assertThat(applied).isGreaterThanOrEqualTo(11);
    }
}
