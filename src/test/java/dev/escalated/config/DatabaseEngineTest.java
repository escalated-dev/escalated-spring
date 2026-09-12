package dev.escalated.config;

import static org.assertj.core.api.Assertions.assertThat;

import hostapp.HostApplication;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The suite runs on the database it was told to run on.
 *
 * A CI matrix leg that quietly fell back to H2 would go green having tested
 * nothing the matrix exists to test, and the failure mode is invisible: every
 * other assertion in the suite still passes. This is the one test that notices.
 */
@SpringBootTest(classes = HostApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.mail.host=localhost"})
class DatabaseEngineTest {

    @Autowired private DataSource dataSource;

    @Test
    void connectsToTheDatabaseTheBuildAskedFor() throws Exception {
        // Set from -Pdatabase=... by the build, so it reaches this JVM by a
        // different route than the connection details do. That is the point: if
        // the environment carrying the URL never arrived, the suite is on H2
        // while CI believes it is on PostgreSQL, and only a value that cannot
        // be stale can tell the difference.
        String expected = System.getProperty("escalated.test.database", "h2");

        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();

            assertThat(product)
                    .as("the build asked for %s and the connection answered %s", expected, product)
                    .isEqualToIgnoringCase(productNameFor(expected));
        }
    }

    private String productNameFor(String database) {
        return switch (database) {
            case "h2" -> "H2";
            case "postgres", "postgresql" -> "PostgreSQL";
            case "mysql" -> "MySQL";
            case "mariadb" -> "MariaDB";
            default -> throw new AssertionError(
                    "-Pdatabase must be h2, postgres, mysql or mariadb; got " + database);
        };
    }

    @Test
    void reachesADatabaseItCanActuallyQuery() throws Exception {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT 1")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }
}
