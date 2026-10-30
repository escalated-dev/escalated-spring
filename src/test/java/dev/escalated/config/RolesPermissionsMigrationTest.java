package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.services.PermissionSeeder;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V25 seeds escalated-laravel's permission set and the {@code admin}, {@code agent} and
 * {@code light_agent} system roles (ADR 2026-10-29-configurable-access-features), and is safe over
 * an install that already has some of them.
 */
class RolesPermissionsMigrationTest {

    private static final String SCRIPT = "V25__seed_laravel_roles_and_permissions.sql";

    @Test
    void bothEnginesHaveTheMigration() {
        for (String vendor : new String[] {"mysql", "postgresql"}) {
            assertThat(new ClassPathResource("db/escalated/" + vendor + "/" + SCRIPT).exists()).as(vendor).isTrue();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ESCALATED_TEST_URL", matches = "jdbc:(postgresql|mysql):.*")
    void theRolesHoldLaravelsPermissions() throws Exception {
        ScratchDatabase database = ScratchDatabase.create("roles");
        try {
            DataSource source = new DriverManagerDataSource(
                    database.url(), ScratchDatabase.username(), ScratchDatabase.password());
            EscalatedMigrations.migrate(source, null);
            JdbcTemplate jdbc = new JdbcTemplate(source);

            List<String> names = jdbc.queryForList("SELECT name FROM escalated_permissions", String.class);
            assertThat(names).containsAll(PermissionSeeder.slugs());
            assertThat(slugsOf(jdbc, "admin")).containsAll(PermissionSeeder.slugs());
            assertThat(slugsOf(jdbc, "light_agent")).containsExactlyInAnyOrder(
                    "ticket.view", "reply.create", "reply.create_internal", "kb.view", "macro.view", "tag.view");
            assertThat(slugsOf(jdbc, "agent")).contains("ticket.close", "reply.create_internal", "audit.view")
                    .doesNotContain("settings.manage", "role.manage");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM escalated_roles WHERE name = 'light_agent'",
                    Integer.class)).isEqualTo(1);
        } finally {
            database.drop();
        }
    }

    private static List<String> slugsOf(JdbcTemplate jdbc, String role) {
        return jdbc.queryForList("SELECT p.name FROM escalated_permissions p JOIN escalated_role_permissions rp"
                + " ON rp.permission_id = p.id JOIN escalated_roles r ON r.id = rp.role_id WHERE r.name = ?",
                String.class, role);
    }
}
