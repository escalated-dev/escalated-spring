package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;

import hostapp.HostApplication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Upgrading a MySQL install whose migrations ran through the host's own Flyway.
 *
 * <p>Up to this release the migrations sat in {@code db/migration}, the
 * location Boot's Flyway reads, so a host with Flyway configured applied
 * V1–V10 into its own {@code flyway_schema_history}. They now run from a
 * location of Escalated's own, into Escalated's own history. An install like
 * that must pick up where it left off — not run V1 again against tables it
 * already has — and the host's Flyway, which can no longer find those ten
 * scripts, must not refuse to start over their rows.
 *
 * <p>MySQL only: the old migrations never ran anywhere else.
 */
@SpringBootTest(classes = HostApplication.class)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:mysql:.*")
@DirtiesContext
@TestPropertySource(
        properties = {
            "spring.mail.host=localhost",
            "spring.jpa.hibernate.ddl-auto=validate",
            "escalated.datasource.migrate=true",
            // The host's own Flyway stays on, as it was for the install being upgraded.
            "spring.flyway.enabled=true",
        })
class MigrationsAdoptedFromTheHostsHistoryTest {

    private static ScratchDatabase database;

    @DynamicPropertySource
    static void anInstallMigratedByTheHostsFlyway(DynamicPropertyRegistry registry) throws IOException {
        database = ScratchDatabase.create("adopt");

        // What 0.1.0 left behind: V1-V10 applied by the host's Flyway and
        // recorded in the host's history table. That could only have happened
        // where V1 runs at all -- MariaDB, or MySQL before 8, which accepted
        // its TEXT default -- so on this server the scripts are replayed
        // without it. Adoption goes by script name, never by checksum.
        Path release = Files.createTempDirectory("escalated-0.1.0-migrations");
        for (String script : EscalatedMigrations.SCRIPTS_FORMERLY_IN_THE_HOSTS_HISTORY) {
            String sql = new ClassPathResource("db/escalated/mysql/" + script).getContentAsString(StandardCharsets.UTF_8);
            Files.writeString(release.resolve(script), sql.replace("abilities TEXT DEFAULT '*',", "abilities TEXT,"));
        }

        Flyway.configure()
                .dataSource(database.url(), ScratchDatabase.username(), ScratchDatabase.password())
                .locations("filesystem:" + release.toAbsolutePath().toString().replace('\\', '/'))
                .table("flyway_schema_history")
                .load()
                .migrate();

        registry.add("spring.datasource.url", database::url);
    }

    @AfterAll
    static void dropDatabase() {
        if (database != null) {
            database.drop();
        }
    }

    @Autowired private JdbcTemplate jdbc;

    /** The host's own Flyway, configured by Boot. */
    @Autowired private Flyway hostFlyway;

    @Test
    void escalatedsHistoryStartsFromWhereTheHostsLeftOff() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM escalated_flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                String.class);

        // A baseline at 10 and the migrations after it. V1-V10 were not run a
        // second time: had they been, V1 would have failed on existing tables.
        assertThat(versions).contains("10", "11").doesNotContain("1", "9");
    }

    @Test
    void theHostsHistoryNoLongerListsEscalatedsMigrations() {
        Integer escalatedRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE script LIKE '%escalated%'", Integer.class);

        assertThat(escalatedRows).isZero();
    }

    @Test
    void theHostsOwnFlywayStillValidatesItsHistory() {
        // The host's Flyway can no longer resolve V1-V10. Had their rows stayed
        // in its history it would report them missing and refuse to migrate.
        assertThat(hostFlyway.getConfiguration().getTable()).isEqualTo("flyway_schema_history");
        assertThat(hostFlyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void existingDataSurvives() {
        assertThat(jdbc.queryForList("SELECT name FROM escalated_roles", String.class))
                .containsExactlyInAnyOrder("admin", "agent", "supervisor");
    }
}
