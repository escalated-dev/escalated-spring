package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V20 adds custom ticket statuses: the system rows, one per built-in status, and a nullable
 * {@code ticket_status_id} on tickets that clears when its status is deleted.
 */
@EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
class TicketStatusesMigrationTest {

    @Test
    void seedsTheSystemRowsAndLeavesExistingTicketsAlone() throws Exception {
        ScratchDatabase database = ScratchDatabase.create("statuses");
        try {
            DataSource source = new DriverManagerDataSource(
                    database.url(), ScratchDatabase.username(), ScratchDatabase.password());
            EscalatedMigrations.migrate(source, null);
            JdbcTemplate jdbc = new JdbcTemplate(source);

            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT slug, category FROM escalated_ticket_statuses WHERE is_system = TRUE ORDER BY slug");
            assertThat(rows).extracting(row -> row.get("slug") + "=" + row.get("category")).containsExactly(
                    "closed=solved", "in_progress=open", "merged=solved", "open=open", "pending=pending",
                    "resolved=solved", "snoozed=on_hold");

            jdbc.update("INSERT INTO escalated_ticket_statuses (label, slug, category) VALUES ('Parts', 'parts', 'on_hold')");
            Long parts = jdbc.queryForObject("SELECT id FROM escalated_ticket_statuses WHERE slug = 'parts'", Long.class);
            Map<String, Object> custom = jdbc.queryForMap("SELECT * FROM escalated_ticket_statuses WHERE id = ?", parts);
            assertThat(custom.get("color")).isEqualTo("#6b7280");
            assertThat(((Number) custom.get("position")).intValue()).isZero();

            jdbc.update("INSERT INTO escalated_tickets (subject, body, status, ticket_number, requester_name,"
                    + " requester_email) VALUES ('Old', 'b', 'OPEN', 'ESC-OLD', 'R', 'r@example.com')");
            assertThat(jdbc.queryForObject(
                    "SELECT ticket_status_id FROM escalated_tickets WHERE ticket_number = 'ESC-OLD'", Long.class))
                    .isNull();
            jdbc.update("INSERT INTO escalated_tickets (subject, body, status, ticket_number, requester_name,"
                    + " requester_email, ticket_status_id) VALUES ('New', 'b', 'IN_PROGRESS', 'ESC-NEW', 'R',"
                    + " 'r@example.com', ?)", parts);

            jdbc.update("DELETE FROM escalated_ticket_statuses WHERE id = ?", parts);
            Map<String, Object> kept = jdbc.queryForMap(
                    "SELECT status, ticket_status_id FROM escalated_tickets WHERE ticket_number = 'ESC-NEW'");
            assertThat(kept.get("status")).isEqualTo("IN_PROGRESS");
            assertThat(kept.get("ticket_status_id")).isNull();
        } finally {
            database.drop();
        }
    }
}
