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

/**
 * V24 adds the plugin contract's tables: {@code escalated_plugins} with a unique slug, and
 * {@code escalated_plugin_store} whose {@code key} (a reserved word on MySQL) and lookup index work.
 */
class PluginTablesMigrationTest {

    @Test
    void bothEnginesHaveTheMigration() {
        for (String vendor : new String[] {"mysql", "postgresql"}) {
            assertThat(new ClassPathResource("db/escalated/" + vendor + "/V24__create_escalated_plugin_tables.sql").exists())
                    .as(vendor).isTrue();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
    void theTablesTakeThePluginRows() throws Exception {
        ScratchDatabase database = ScratchDatabase.create("plugins");
        try {
            DataSource source = new DriverManagerDataSource(
                    database.url(), ScratchDatabase.username(), ScratchDatabase.password());
            EscalatedMigrations.migrate(source, null);
            JdbcTemplate jdbc = new JdbcTemplate(source);

            jdbc.update("INSERT INTO escalated_plugins (slug, is_active) VALUES ('acme--billing', TRUE)");
            assertThat(jdbc.queryForObject("SELECT is_active FROM escalated_plugins WHERE slug = 'acme--billing'",
                    Boolean.class)).isTrue();
            assertThatThrownBy(() -> jdbc.update("INSERT INTO escalated_plugins (slug) VALUES ('acme--billing')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT is_active FROM escalated_plugins WHERE slug = 'acme--billing'",
                    Boolean.class)).isTrue();

            String key;
            try (var connection = source.getConnection()) {
                key = connection.getMetaData().getIdentifierQuoteString().trim();
            }
            String column = key + "key" + key;
            jdbc.update("INSERT INTO escalated_plugin_store (plugin, collection, " + column + ", data) VALUES (?, ?, ?, ?)",
                    "@escalated-dev/" + "p".repeat(170), "c".repeat(100), "k".repeat(191), "{\"a\":1}");
            jdbc.update("INSERT INTO escalated_plugin_store (plugin, collection, " + column + ", data) VALUES (?, ?, ?, ?)",
                    "acme/billing", "__config__", null, "{}");
            assertThat(jdbc.queryForObject("SELECT data FROM escalated_plugin_store WHERE collection = ?", String.class,
                    "c".repeat(100))).isEqualTo("{\"a\":1}");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM escalated_plugin_store WHERE " + column + " IS NULL",
                    Integer.class)).isEqualTo(1);
        } finally {
            database.drop();
        }
    }
}
