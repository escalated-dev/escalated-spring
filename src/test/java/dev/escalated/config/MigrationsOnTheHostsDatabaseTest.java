package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;

import hostapp.HostApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Escalated's migrations, run on an empty database, produce the schema its
 * entities map — on every engine a host deploys on.
 *
 * <p>The rest of the suite builds its schema with {@code ddl-auto=create-drop},
 * so it has never run a migration. This boots a host the way the README
 * configures one — Hibernate validating, migrations owning the schema — and the
 * context starting at all is the assertion: {@code validate} refuses to start
 * against a table or column the entities expect and the migrations never made.
 */
@SpringBootTest(classes = HostApplication.class)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
@DirtiesContext
@TestPropertySource(
        properties = {
            "spring.mail.host=localhost",
            "spring.jpa.hibernate.ddl-auto=validate",
            "escalated.datasource.migrate=true",
        })
class MigrationsOnTheHostsDatabaseTest {

    private static ScratchDatabase database;

    @DynamicPropertySource
    static void emptyDatabase(DynamicPropertyRegistry registry) {
        database = ScratchDatabase.create("shared");
        registry.add("spring.datasource.url", database::url);
    }

    @AfterAll
    static void dropDatabase() {
        if (database != null) {
            database.drop();
        }
    }

    @Autowired private JdbcTemplate jdbc;

    @Test
    void everyMigrationIsRecordedInEscalatedsOwnHistory() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM escalated_flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                Integer.class);

        assertThat(applied).isGreaterThanOrEqualTo(11);
    }

    @Test
    void theDefaultRolesAreSeeded() {
        assertThat(jdbc.queryForList("SELECT name FROM escalated_roles", String.class))
                .containsExactlyInAnyOrder("admin", "agent", "supervisor");
    }

    @Test
    void escalatedLeavesTheHostsOwnFlywayHistoryAlone() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection();
                var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "flyway_schema_history", null)) {
            assertThat(tables.next()).isFalse();
        }
    }
}
