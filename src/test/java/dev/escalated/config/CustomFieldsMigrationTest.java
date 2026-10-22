package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * V19 gives custom fields escalated-laravel's remaining columns without moving any data, keeps one
 * value per field per ticket, and adds custom objects whose records go with them.
 */
@EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
class CustomFieldsMigrationTest {

    @Test
    void keepsVersionEighteenFieldsAndTheNewestOfEachValue() throws Exception {
        ScratchDatabase database = ScratchDatabase.create("custom_fields");
        try {
            DataSource source = new DriverManagerDataSource(
                    database.url(), ScratchDatabase.username(), ScratchDatabase.password());
            migrateTo(source, "18");
            JdbcTemplate jdbc = new JdbcTemplate(source);

            jdbc.update("INSERT INTO escalated_tickets (subject,body,ticket_number,requester_name,requester_email)"
                    + " VALUES ('Existing','Body','MIGRATION-CF','Customer','customer@example.test')");
            Long ticket = jdbc.queryForObject(
                    "SELECT id FROM escalated_tickets WHERE ticket_number = 'MIGRATION-CF'", Long.class);
            jdbc.update("INSERT INTO escalated_custom_fields (name, field_key, field_type, options, sort_order)"
                    + " VALUES ('Plan', 'plan', 'select', 'free,pro', 3)");
            Long field = jdbc.queryForObject("SELECT id FROM escalated_custom_fields WHERE field_key = 'plan'", Long.class);
            jdbc.update("INSERT INTO escalated_custom_field_values (ticket_id, custom_field_id, field_value)"
                    + " VALUES (?, ?, 'free')", ticket, field);
            jdbc.update("INSERT INTO escalated_custom_field_values (ticket_id, custom_field_id, field_value)"
                    + " VALUES (?, ?, 'pro')", ticket, field);

            EscalatedMigrations.migrate(source, null);

            Map<String, Object> migrated = jdbc.queryForMap("SELECT * FROM escalated_custom_fields WHERE id = ?", field);
            assertThat(migrated.get("context")).isEqualTo("ticket");
            assertThat(migrated.get("field_key")).isEqualTo("plan");
            assertThat(migrated.get("options")).isEqualTo("free,pro");
            assertThat(((Number) migrated.get("sort_order")).intValue()).isEqualTo(3);
            assertThat(migrated).containsKeys("placeholder", "validation_rules", "conditions");

            assertThat(jdbc.queryForList("SELECT field_value FROM escalated_custom_field_values WHERE ticket_id = ?",
                    String.class, ticket)).containsExactly("pro");
            assertThatThrownBy(() -> jdbc.update("INSERT INTO escalated_custom_field_values"
                    + " (ticket_id, custom_field_id, field_value) VALUES (?, ?, 'again')", ticket, field))
                    .isInstanceOf(DataIntegrityViolationException.class);

            jdbc.update("INSERT INTO escalated_custom_objects (name, slug, fields_schema)"
                    + " VALUES ('Assets', 'assets', '[{\"name\":\"serial\",\"type\":\"text\"}]')");
            Long object = jdbc.queryForObject("SELECT id FROM escalated_custom_objects WHERE slug = 'assets'", Long.class);
            jdbc.update("INSERT INTO escalated_custom_object_records (object_id, data) VALUES (?, '{\"serial\":\"S\"}')",
                    object);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO escalated_custom_objects (name, slug, fields_schema)"
                    + " VALUES ('Again', 'assets', '[]')")).isInstanceOf(DataIntegrityViolationException.class);

            jdbc.update("DELETE FROM escalated_custom_objects WHERE id = ?", object);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM escalated_custom_object_records", Integer.class))
                    .isZero();
        } finally {
            database.drop();
        }
    }

    private static void migrateTo(DataSource source, String target) throws Exception {
        String vendor;
        try (var connection = source.getConnection()) {
            vendor = EscalatedMigrations.vendorOf(connection.getMetaData());
        }
        if ("mysql".equals(vendor)) {
            ReflectionTestUtils.invokeMethod(EscalatedMigrations.class, "createMySqlV1", source);
        }
        Flyway.configure()
                .dataSource(source)
                .locations(EscalatedMigrations.LOCATION + vendor)
                .table(EscalatedMigrations.HISTORY_TABLE)
                .baselineOnMigrate(true)
                .baselineVersion("mysql".equals(vendor) ? "1" : "0")
                .target(target)
                .load()
                .migrate();
    }
}
