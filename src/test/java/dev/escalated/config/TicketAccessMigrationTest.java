package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

@EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
class TicketAccessMigrationTest {
    @Test
    void upgradesPopulatedVersionElevenWithoutChangingExistingRows() throws Exception {
        ScratchDatabase database = ScratchDatabase.create("access_upgrade");
        try {
            var source =
                    new DriverManagerDataSource(
                            database.url(), ScratchDatabase.username(), ScratchDatabase.password());
            String vendor;
            try (var connection = source.getConnection()) {
                vendor = EscalatedMigrations.vendorOf(connection.getMetaData());
            }
            if ("mysql".equals(vendor)) {
                ReflectionTestUtils.invokeMethod(
                        EscalatedMigrations.class, "createMySqlV1", source);
            }
            Flyway.configure()
                    .dataSource(source)
                    .locations(EscalatedMigrations.LOCATION + vendor)
                    .table(EscalatedMigrations.HISTORY_TABLE)
                    .baselineOnMigrate(true)
                    .baselineVersion("mysql".equals(vendor) ? "1" : "0")
                    .target("11")
                    .load()
                    .migrate();
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.update(
                    "INSERT INTO escalated_tickets"
                            + " (subject,body,ticket_number,requester_name,requester_email) VALUES"
                            + " ('Existing request','Existing"
                            + " body','MIGRATION-1','Customer','customer@example.test')");
            var before =
                    jdbc.queryForMap(
                            "SELECT subject,body,status,priority,created_at FROM"
                                    + " escalated_tickets");
            EscalatedMigrations.migrate(source, null);
            assertThat(
                            jdbc.queryForMap(
                                    "SELECT subject,body,status,priority,created_at FROM"
                                            + " escalated_tickets"))
                    .isEqualTo(before);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM escalated_tickets WHERE account_type IS"
                                        + " NULL AND account_id IS NULL AND ticket_type IS NULL",
                                    Integer.class))
                    .isEqualTo(1);
            jdbc.update(
                    "UPDATE escalated_tickets SET account_type = 'Company', account_id = '007'");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM escalated_tickets WHERE account_type ="
                                            + " 'company'",
                                    Integer.class))
                    .isZero();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM escalated_tickets WHERE account_id = '7'",
                                    Integer.class))
                    .isZero();
            EscalatedMigrations.migrate(source, null);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM escalated_tickets", Integer.class))
                    .isEqualTo(1);
        } finally {
            database.drop();
        }
    }
}
