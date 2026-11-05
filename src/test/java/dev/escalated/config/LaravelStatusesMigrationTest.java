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
 * V29 adds system rows for escalated-laravel's {@code escalated}, {@code reopened} and {@code live}
 * statuses. A custom status that already used one of those slugs keeps its id (so tickets keep
 * it) under a free slug, since the slug now names the built-in status.
 */
@EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
class LaravelStatusesMigrationTest {

    @Test
    void addsTheSystemRowsAndMovesACustomStatusOffTheirSlugs() throws Exception {
        ScratchDatabase database = ScratchDatabase.create("laravelstatuses");
        try {
            DataSource source = new DriverManagerDataSource(
                    database.url(), ScratchDatabase.username(), ScratchDatabase.password());
            EscalatedMigrations.migrate(source, null, "28");
            JdbcTemplate jdbc = new JdbcTemplate(source);

            jdbc.update("INSERT INTO escalated_ticket_statuses (label, slug, category) VALUES ('Live', 'live', 'open')");
            Long custom = jdbc.queryForObject("SELECT id FROM escalated_ticket_statuses WHERE slug = 'live'", Long.class);
            jdbc.update("INSERT INTO escalated_tickets (subject, body, status, ticket_number, requester_name,"
                    + " requester_email, ticket_status_id) VALUES ('T', 'b', 'OPEN', 'ESC-L', 'R', 'r@example.com', ?)",
                    custom);

            EscalatedMigrations.migrate(source, null);

            List<Map<String, Object>> rows = jdbc.queryForList("SELECT slug, category, color, is_system"
                    + " FROM escalated_ticket_statuses WHERE slug IN ('escalated', 'reopened', 'live') ORDER BY slug");
            assertThat(rows).extracting(row -> row.get("slug") + "=" + row.get("category") + "=" + row.get("color")
                    + "=" + row.get("is_system")).containsExactly(
                    "escalated=open=#ef4444=true", "live=open=#06b6d4=true", "reopened=open=#3b82f6=true");
            assertThat(jdbc.queryForObject("SELECT slug FROM escalated_ticket_statuses WHERE id = ?", String.class, custom))
                    .isEqualTo("live_2");
            assertThat(jdbc.queryForObject("SELECT ticket_status_id FROM escalated_tickets WHERE ticket_number = 'ESC-L'",
                    Long.class)).isEqualTo(custom);
            assertThat(jdbc.queryForObject("SELECT status FROM escalated_tickets WHERE ticket_number = 'ESC-L'",
                    String.class)).isEqualTo("OPEN");
        } finally {
            database.drop();
        }
    }
}
