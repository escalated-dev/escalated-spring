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

/** V27 adds {@code escalated_sso_message_ids}: unique on kind and message ID, expiry in Unix seconds. */
class SsoMessageIdsMigrationTest {

    @Test
    void bothEnginesHaveTheMigration() {
        for (String vendor : new String[] {"mysql", "postgresql"}) {
            assertThat(new ClassPathResource("db/escalated/" + vendor + "/V27__create_escalated_sso_message_ids.sql")
                    .exists()).as(vendor).isTrue();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
    void anIdIsKeptOncePerKind() throws Exception {
        ScratchDatabase database = ScratchDatabase.create("ssoids");
        try {
            DataSource source = new DriverManagerDataSource(
                    database.url(), ScratchDatabase.username(), ScratchDatabase.password());
            EscalatedMigrations.migrate(source, null);
            JdbcTemplate jdbc = new JdbcTemplate(source);
            String id = "_" + "a".repeat(190);
            jdbc.update("INSERT INTO escalated_sso_message_ids (kind, message_id, expires_at) VALUES (?, ?, ?)",
                    "saml_assertion", id, 4_102_444_800L);
            jdbc.update("INSERT INTO escalated_sso_message_ids (kind, message_id, expires_at) VALUES (?, ?, ?)",
                    "jwt", id, 4_102_444_800L);
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO escalated_sso_message_ids (kind, message_id, expires_at) VALUES (?, ?, ?)",
                    "jwt", id, 1L)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM escalated_sso_message_ids WHERE expires_at > ?",
                    Integer.class, 1_700_000_000L)).isEqualTo(2);
        } finally {
            database.drop();
        }
    }
}
