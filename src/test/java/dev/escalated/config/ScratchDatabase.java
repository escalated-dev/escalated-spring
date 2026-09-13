package dev.escalated.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An empty database of a test's own, on the server the suite is running against.
 *
 * <p>Migration tests need a database nothing else has touched. The suite's own
 * database is no good for that: every {@code create-drop} context in the run
 * leaves Escalated's tables in it, and a migration that meets them fails for a
 * reason that has nothing to do with the migration. A second database on the
 * same server is empty, and cannot be seen from the first — which a schema,
 * on PostgreSQL, can be.
 */
final class ScratchDatabase {

    private static final Pattern SERVER_URL =
            Pattern.compile("^(jdbc:(postgresql|mysql)://[^/]+/)([^?]*)(\\?.*)?$");

    private final String serverUrl;
    private final String name;
    private final String url;
    private final boolean postgres;

    private ScratchDatabase(String serverUrl, String name, String url, boolean postgres) {
        this.serverUrl = serverUrl;
        this.name = name;
        this.url = url;
        this.postgres = postgres;
    }

    /** Creates the database. {@code ESCALATED_TEST_URL} must name a PostgreSQL or MySQL server. */
    static ScratchDatabase create(String purpose) {
        String serverUrl = System.getenv("ESCALATED_TEST_URL");
        Matcher matcher = serverUrl == null ? null : SERVER_URL.matcher(serverUrl);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalStateException(
                    "a scratch database needs ESCALATED_TEST_URL to name a PostgreSQL or MySQL server; got " + serverUrl);
        }

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String name = "esc_" + purpose + "_" + suffix;
        String params = matcher.group(4) == null ? "" : matcher.group(4);

        ScratchDatabase database = new ScratchDatabase(
                serverUrl, name, matcher.group(1) + name + params, "postgresql".equals(matcher.group(2)));
        database.execute("CREATE DATABASE " + name);
        return database;
    }

    String url() {
        return url;
    }

    static String username() {
        return System.getenv("ESCALATED_TEST_USERNAME");
    }

    static String password() {
        String password = System.getenv("ESCALATED_TEST_PASSWORD");
        return password == null ? "" : password;
    }

    void drop() {
        // FORCE, because the test's connection pool may still be open when this
        // runs; PostgreSQL otherwise refuses to drop a database in use.
        execute(postgres
                ? "DROP DATABASE IF EXISTS " + name + " WITH (FORCE)"
                : "DROP DATABASE IF EXISTS " + name);
    }

    private void execute(String sql) {
        try (Connection connection = DriverManager.getConnection(serverUrl, username(), password());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException(sql, ex);
        }
    }
}
