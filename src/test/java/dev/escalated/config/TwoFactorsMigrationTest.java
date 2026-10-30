package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V26 adds {@code escalated_two_factors}: one enrolment per agent profile, gone with the profile. */
class TwoFactorsMigrationTest {

    @Test
    void bothEnginesHaveTheMigration() {
        for (String vendor : new String[] {"mysql", "postgresql"}) {
            assertThat(new ClassPathResource("db/escalated/" + vendor + "/V26__create_escalated_two_factors.sql").exists())
                    .as(vendor).isTrue();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
    void theTableTakesOneEnrolmentPerProfile() throws Exception {
        ScratchDatabase database = ScratchDatabase.create("twofactors");
        try {
            DataSource source = new DriverManagerDataSource(
                    database.url(), ScratchDatabase.username(), ScratchDatabase.password());
            EscalatedMigrations.migrate(source, null);
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.update("INSERT INTO escalated_agent_profiles (name, email) VALUES ('A', 'a@example.com')");
            Long agent = jdbc.queryForObject("SELECT id FROM escalated_agent_profiles WHERE email = 'a@example.com'",
                    Long.class);
            jdbc.update("INSERT INTO escalated_two_factors (agent_id, secret, recovery_codes, last_used_step)"
                    + " VALUES (?, ?, ?, ?)", agent, "x".repeat(500), "y".repeat(2000), 58_000_000L);
            assertThat(jdbc.queryForObject("SELECT confirmed_at FROM escalated_two_factors", Object.class)).isNull();
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO escalated_two_factors (agent_id, secret) VALUES (?, 's')", agent))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("DELETE FROM escalated_agent_profiles WHERE id = ?", agent);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM escalated_two_factors", Integer.class)).isZero();
        } finally {
            database.drop();
        }
    }
}
