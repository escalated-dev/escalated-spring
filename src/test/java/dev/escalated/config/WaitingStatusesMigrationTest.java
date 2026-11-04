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
 * V28 splits the old {@code PENDING} status into escalated-laravel's waiting statuses: stored
 * {@code PENDING} tickets become {@code WAITING_ON_CUSTOMER}, the {@code pending} system row becomes
 * {@code waiting_on_customer}, and {@code waiting_on_agent} gets a system row of its own.
 */
@EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
class WaitingStatusesMigrationTest {

    @Test
    void pendingTicketsBecomeWaitingOnTheCustomer() throws Exception {
        ScratchDatabase database = ScratchDatabase.create("waiting");
        try {
            DataSource source = new DriverManagerDataSource(
                    database.url(), ScratchDatabase.username(), ScratchDatabase.password());
            EscalatedMigrations.migrate(source, null, "27");
            JdbcTemplate jdbc = new JdbcTemplate(source);

            Long parts = jdbc.queryForObject("SELECT id FROM escalated_ticket_statuses WHERE slug = 'pending'", Long.class);
            jdbc.update("INSERT INTO escalated_ticket_statuses (label, slug, category) VALUES ('Parts', 'parts', 'pending')");
            Long custom = jdbc.queryForObject("SELECT id FROM escalated_ticket_statuses WHERE slug = 'parts'", Long.class);
            for (String[] row : new String[][] {{"ESC-P", "PENDING"}, {"ESC-O", "OPEN"}, {"ESC-C", "PENDING"}}) {
                jdbc.update("INSERT INTO escalated_tickets (subject, body, status, ticket_number, requester_name,"
                        + " requester_email) VALUES ('T', 'b', ?, ?, 'R', 'r@example.com')", row[1], row[0]);
            }
            jdbc.update("UPDATE escalated_tickets SET ticket_status_id = ? WHERE ticket_number = 'ESC-C'", custom);

            EscalatedMigrations.migrate(source, null);

            assertThat(status(jdbc, "ESC-P")).isEqualTo("WAITING_ON_CUSTOMER");
            assertThat(status(jdbc, "ESC-O")).isEqualTo("OPEN");
            // A custom status of the pending category keeps its custom status, anchored on the new value.
            assertThat(status(jdbc, "ESC-C")).isEqualTo("WAITING_ON_CUSTOMER");
            assertThat(jdbc.queryForObject("SELECT ticket_status_id FROM escalated_tickets WHERE ticket_number = 'ESC-C'",
                    Long.class)).isEqualTo(custom);

            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT slug, category FROM escalated_ticket_statuses WHERE is_system = TRUE ORDER BY slug");
            assertThat(rows).extracting(row -> row.get("slug") + "=" + row.get("category")).containsExactly(
                    "closed=solved", "escalated=open", "in_progress=open", "live=open", "merged=solved", "open=open",
                    "reopened=open", "resolved=solved", "snoozed=on_hold", "waiting_on_agent=open",
                    "waiting_on_customer=pending");
            // The pending system row was renamed, not replaced: its id is kept.
            assertThat(jdbc.queryForObject(
                    "SELECT id FROM escalated_ticket_statuses WHERE slug = 'waiting_on_customer'", Long.class))
                    .isEqualTo(parts);
            assertThat(jdbc.queryForObject(
                    "SELECT label FROM escalated_ticket_statuses WHERE slug = 'waiting_on_customer'", String.class))
                    .isEqualTo("Waiting on Customer");
        } finally {
            database.drop();
        }
    }

    private static String status(JdbcTemplate jdbc, String reference) {
        return jdbc.queryForObject("SELECT status FROM escalated_tickets WHERE ticket_number = ?", String.class, reference);
    }
}
