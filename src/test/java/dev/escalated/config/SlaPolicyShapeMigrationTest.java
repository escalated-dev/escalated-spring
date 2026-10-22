package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * V18 converts SLA policies and escalation rules saved in this port's old shape (one priority per
 * policy, rules hanging off a policy) into escalated-laravel's, so that they keep doing what they did.
 */
@EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
class SlaPolicyShapeMigrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void convertsVersionSeventeenPoliciesAndRules() throws Exception {
        ScratchDatabase database = ScratchDatabase.create("sla_shape");
        try {
            DataSource source = new DriverManagerDataSource(
                    database.url(), ScratchDatabase.username(), ScratchDatabase.password());
            migrateTo(source, "17");
            JdbcTemplate jdbc = new JdbcTemplate(source);

            jdbc.update("INSERT INTO escalated_business_schedules (name) VALUES ('Office')");
            Long schedule = jdbc.queryForObject("SELECT MAX(id) FROM escalated_business_schedules", Long.class);
            jdbc.update("INSERT INTO escalated_sla_policies (name, priority, first_response_minutes,"
                    + " resolution_minutes, is_active) VALUES ('High', 'HIGH', 240, 1440, TRUE)");
            Long high = jdbc.queryForObject("SELECT id FROM escalated_sla_policies WHERE name = 'High'", Long.class);
            jdbc.update("INSERT INTO escalated_sla_policies (name, priority, first_response_minutes,"
                    + " resolution_minutes, is_active, business_schedule_id, department_id, ticket_type)"
                    + " VALUES ('Urgent delivery', 'URGENT', 30, 90, FALSE, ?, 7, 'delivery')", schedule);
            Long urgent = jdbc.queryForObject(
                    "SELECT id FROM escalated_sla_policies WHERE name = 'Urgent delivery'", Long.class);

            jdbc.update("INSERT INTO escalated_escalation_rules (sla_policy_id, name, minutes_before_or_after,"
                    + " action_type, action_target, conditions, repeat_after_hours)"
                    + " VALUES (?, 'Reassign', 30, 'reassign', '42', ?, 6)", high,
                    "{\"all\":[{\"field\":\"ticket_type\",\"operator\":\"equals\",\"value\":\"delivery\"}]}");
            jdbc.update("INSERT INTO escalated_escalation_rules (sla_policy_id, name, minutes_before_or_after,"
                    + " action_type, notify_emails, is_active)"
                    + " VALUES (?, 'Tell the lead', -15, 'notify', ?, FALSE)", urgent,
                    "lead@example.com,\"Night\" <night@example.com>\r\nC:\\shared\\ops");
            jdbc.update("INSERT INTO escalated_escalation_rules (sla_policy_id, name, action_type, action_target)"
                    + " VALUES (?, 'Bump', 'change_priority', 'URGENT')", high);
            jdbc.update("INSERT INTO escalated_escalation_rules (sla_policy_id, name, action_type)"
                    + " VALUES (?, 'Nobody', 'reassign')", high);

            EscalatedMigrations.migrate(source, null);

            Map<String, Object> converted = jdbc.queryForMap(
                    "SELECT * FROM escalated_sla_policies WHERE id = ?", high);
            assertThat(JSON.readTree((String) converted.get("first_response_hours")))
                    .isEqualTo(JSON.readTree("{\"high\":4}"));
            assertThat(JSON.readTree((String) converted.get("resolution_hours")))
                    .isEqualTo(JSON.readTree("{\"high\":24}"));
            assertThat(converted.get("is_default")).isEqualTo(true);
            assertThat(converted.get("business_hours_only")).isEqualTo(false);
            assertThat(converted).doesNotContainKeys("priority", "first_response_minutes", "resolution_minutes");

            Map<String, Object> targeted = jdbc.queryForMap(
                    "SELECT * FROM escalated_sla_policies WHERE id = ?", urgent);
            assertThat(JSON.readTree((String) targeted.get("first_response_hours")).get("urgent").asDouble())
                    .isEqualTo(0.5);
            assertThat(JSON.readTree((String) targeted.get("resolution_hours")).get("urgent").asDouble())
                    .isEqualTo(1.5);
            assertThat(targeted.get("business_hours_only")).isEqualTo(true);
            assertThat(((Number) targeted.get("business_schedule_id")).longValue()).isEqualTo(schedule);
            assertThat(((Number) targeted.get("department_id")).longValue()).isEqualTo(7L);
            assertThat(targeted.get("ticket_type")).isEqualTo("delivery");
            assertThat(targeted.get("is_active")).isEqualTo(false);

            Map<String, Object> reassign = rule(jdbc, "Reassign");
            assertThat(JSON.readTree((String) reassign.get("conditions"))).isEqualTo(JSON.readTree(
                    "[{\"field\":\"sla_policy_id\",\"value\":\"" + high + "\"},"
                            + "{\"field\":\"sla_breached\",\"value\":\"resolution\"},"
                            + "{\"field\":\"workflow_conditions\",\"value\":{\"all\":[{\"field\":\"ticket_type\","
                            + "\"operator\":\"equals\",\"value\":\"delivery\"}]}}]"));
            assertThat(JSON.readTree((String) reassign.get("actions")))
                    .isEqualTo(JSON.readTree("[{\"type\":\"assign_to\",\"value\":\"42\"}]"));
            assertThat(((Number) reassign.get("repeat_after_hours")).intValue()).isEqualTo(6);
            assertThat(reassign.get("trigger_type")).isEqualTo("sla_breach");
            assertThat(reassign.get("category")).isEqualTo("Uncategorized");
            assertThat(reassign).doesNotContainKeys("sla_policy_id", "action_type", "action_target",
                    "notify_emails", "minutes_before_or_after");

            Map<String, Object> notify = rule(jdbc, "Tell the lead");
            JsonNode notifyAction = JSON.readTree((String) notify.get("actions")).get(0);
            assertThat(notifyAction.get("type").asText()).isEqualTo("notify");
            assertThat(notifyAction.get("value").asText())
                    .isEqualTo("lead@example.com,\"Night\" <night@example.com>\r\nC:\\shared\\ops");
            assertThat(JSON.readTree((String) notify.get("conditions")).size()).isEqualTo(2);
            assertThat(notify.get("is_active")).isEqualTo(false);

            assertThat(JSON.readTree((String) rule(jdbc, "Bump").get("actions")))
                    .isEqualTo(JSON.readTree("[{\"type\":\"change_priority\",\"value\":\"urgent\"}]"));
            assertThat(JSON.readTree((String) rule(jdbc, "Nobody").get("actions")))
                    .isEqualTo(JSON.readTree("[]"));

            // The old order (minutes_before_or_after, then id) is the position.
            assertThat(position(notify)).isZero();
            assertThat(position(rule(jdbc, "Bump"))).isEqualTo(1);
            assertThat(position(rule(jdbc, "Nobody"))).isEqualTo(2);
            assertThat(position(reassign)).isEqualTo(3);

            // A rule no longer goes with its policy.
            jdbc.update("DELETE FROM escalated_sla_policies WHERE id = ?", high);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM escalated_escalation_rules", Integer.class))
                    .isEqualTo(4);
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

    private static Map<String, Object> rule(JdbcTemplate jdbc, String name) {
        return jdbc.queryForMap("SELECT * FROM escalated_escalation_rules WHERE name = ?", name);
    }

    private static int position(Map<String, Object> rule) {
        return ((Number) rule.get("sort_order")).intValue();
    }
}
