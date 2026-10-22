package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V22 adds {@code is_default} to business schedules, false for the rows already there. */
@EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
class BusinessScheduleDefaultMigrationTest {

    @Test
    void schedulesAreNotTheDefaultUnlessSet() throws Exception {
        ScratchDatabase database = ScratchDatabase.create("bizdefault");
        try {
            DataSource source = new DriverManagerDataSource(
                    database.url(), ScratchDatabase.username(), ScratchDatabase.password());
            EscalatedMigrations.migrate(source, null);
            JdbcTemplate jdbc = new JdbcTemplate(source);

            jdbc.update("INSERT INTO escalated_business_schedules (name) VALUES ('Office')");
            assertThat(jdbc.queryForObject(
                    "SELECT is_default FROM escalated_business_schedules WHERE name = 'Office'", Boolean.class))
                    .isFalse();
            jdbc.update("INSERT INTO escalated_business_schedules (name, is_default) VALUES ('Support', TRUE)");
            assertThat(jdbc.queryForObject(
                    "SELECT is_default FROM escalated_business_schedules WHERE name = 'Support'", Boolean.class))
                    .isTrue();
        } finally {
            database.drop();
        }
    }
}
