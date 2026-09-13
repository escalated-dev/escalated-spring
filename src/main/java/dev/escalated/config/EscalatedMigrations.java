package dev.escalated.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Runs Escalated's migrations against the database its tables live on.
 *
 * <p>The migrations live in {@code classpath:db/escalated/{vendor}} — one
 * edition per engine, PostgreSQL and MySQL (MariaDB uses MySQL's) — and are
 * recorded in {@value #HISTORY_TABLE}, never in the host's own Flyway history.
 * Escalated runs them itself, in both of its database arrangements: nothing
 * else will. Boot's own Flyway support is a separate module a host may not
 * have, and where it does have it, sharing its location and history table
 * meant sharing version numbers with the host's own migrations.
 *
 * <p>Earlier releases shipped the MySQL scripts in {@code db/migration}, where
 * a host with Boot's Flyway applied them into the host's
 * {@code flyway_schema_history}. Such an install is adopted, not re-run:
 * Escalated's history is baselined at the highest of those versions, and their
 * rows are removed from the host's history — which no longer has the scripts
 * to validate them against, and would refuse to start over them otherwise.
 *
 * <p>MySQL's V1 declares {@code abilities TEXT DEFAULT '*'}, which MySQL 8
 * rejects outright, strict mode or not; it only ever ran on MariaDB and older
 * MySQL. The script cannot change — installs that did run it have its checksum
 * in their history — so a fresh MySQL install runs it minus that one default
 * ({@code ApiToken} sets the value itself) and is baselined at version 1.
 *
 * <p>A database that already has Escalated's tables but no record of either
 * kind had its schema made some other way, typically by Hibernate's
 * {@code ddl-auto}. It is left exactly as it is, with a warning, as it always
 * was.
 */
public class EscalatedMigrations implements InitializingBean {

    /** Escalated's own Flyway schema history table. */
    public static final String HISTORY_TABLE = "escalated_flyway_schema_history";

    static final String LOCATION = "classpath:db/escalated/";

    /**
     * The scripts earlier releases shipped in {@code db/migration}, where a
     * host's Flyway recorded them in the host's history. Matched by exact file
     * name, so no row of the host's own is ever touched.
     */
    static final Set<String> SCRIPTS_FORMERLY_IN_THE_HOSTS_HISTORY = Set.of(
            "V1__create_escalated_tables.sql",
            "V2__create_escalated_contacts.sql",
            "V3__add_user_role_flags.sql",
            "V4__create_escalated_agent_skills.sql",
            "V5__create_escalated_skill_routing_tags.sql",
            "V6__create_escalated_skill_routing_departments.sql",
            "V7__create_escalated_ticket_subjects.sql",
            "V8__create_escalated_newsletters.sql",
            "V9__newsletter_next_attempt_and_permissions.sql",
            "V10__create_escalated_ticket_followers.sql");

    static final String MYSQL_V1 = "db/escalated/mysql/V1__create_escalated_tables.sql";

    private static final String MYSQL_V1_REJECTED_DEFAULT = "abilities TEXT DEFAULT '*',";

    private static final Pattern PLAIN_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Logger log = LoggerFactory.getLogger(EscalatedMigrations.class);

    private final DataSource dataSource;
    private final String hostHistoryTable;

    /**
     * @param hostHistoryTable the host's Flyway history table to adopt earlier
     *     migrations from, or {@code null} when the database is not the host's
     */
    public EscalatedMigrations(DataSource dataSource, String hostHistoryTable) {
        this.dataSource = dataSource;
        this.hostHistoryTable = hostHistoryTable;
    }

    @Override
    public void afterPropertiesSet() {
        migrate(dataSource, hostHistoryTable);
    }

    /** Migrates {@code dataSource}; see the class documentation. */
    public static void migrate(DataSource dataSource, String hostHistoryTable) {
        Inspection database = inspect(dataSource, hostHistoryTable);

        if (!database.hasHistory && database.adoptable.isEmpty() && database.hasTables) {
            log.warn("Escalated's tables already exist but neither {} nor the host's Flyway history records a"
                    + " migration of them, so their schema was created some other way (Hibernate ddl-auto, by"
                    + " hand). Escalated's migrations were not run. Set escalated.datasource.migrate=false to"
                    + " silence this, or baseline {} to hand the schema to the migrations.",
                    HISTORY_TABLE, HISTORY_TABLE);
            return;
        }

        boolean adopting = !database.hasHistory && !database.adoptable.isEmpty();
        boolean fresh = !database.hasHistory && database.adoptable.isEmpty() && !database.hasTables;

        String baseline = "0";
        if (adopting) {
            baseline = highest(database.adoptable).getVersion();
        } else if (fresh && "mysql".equals(database.vendor)) {
            createMySqlV1(dataSource);
            baseline = "1";
        }

        Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION + database.vendor)
                .table(HISTORY_TABLE)
                // A host's database is never empty, so without a baseline
                // Flyway refuses to migrate it at all. Version 0 baselines
                // below V1, so a fresh install still runs every migration.
                .baselineOnMigrate(true)
                .baselineVersion(baseline)
                .baselineDescription(adopting
                        ? "Adopted from " + hostHistoryTable
                        : "<< Escalated baseline >>")
                .load()
                .migrate();

        if (!database.adoptable.isEmpty()) {
            removeFromHostHistory(dataSource, hostHistoryTable);
        }
    }

    /** Runs MySQL's V1 without the one default MySQL 8 refuses; see the class documentation. */
    private static void createMySqlV1(DataSource dataSource) {
        String script;
        try {
            script = new ClassPathResource(MYSQL_V1, EscalatedMigrations.class.getClassLoader())
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + MYSQL_V1, ex);
        }
        if (script.indexOf(MYSQL_V1_REJECTED_DEFAULT) < 0
                || script.indexOf(MYSQL_V1_REJECTED_DEFAULT) != script.lastIndexOf(MYSQL_V1_REJECTED_DEFAULT)) {
            throw new IllegalStateException(MYSQL_V1 + " no longer contains exactly one \""
                    + MYSQL_V1_REJECTED_DEFAULT + "\"; it must never change once released");
        }
        byte[] runnable = script.replace(MYSQL_V1_REJECTED_DEFAULT, "abilities TEXT,").getBytes(StandardCharsets.UTF_8);

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ByteArrayResource(runnable, MYSQL_V1), StandardCharsets.UTF_8));
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not run " + MYSQL_V1, ex);
        }
    }

    private static Inspection inspect(DataSource dataSource, String hostHistoryTable) {
        try (Connection connection = dataSource.getConnection()) {
            Inspection inspection = new Inspection();
            inspection.vendor = vendorOf(connection.getMetaData());
            inspection.hasHistory = tableExists(connection, HISTORY_TABLE);
            inspection.hasTables = tableExists(connection, "escalated_tickets");
            inspection.adoptable = adoptableVersions(connection, hostHistoryTable);
            return inspection;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not inspect the database before running Escalated's migrations", ex);
        }
    }

    static String vendorOf(DatabaseMetaData metaData) throws SQLException {
        String product = metaData.getDatabaseProductName();
        String lower = product.toLowerCase(Locale.ROOT);

        if (lower.contains("postgresql")) {
            return "postgresql";
        }
        if (lower.contains("mysql") || lower.contains("mariadb")) {
            return "mysql";
        }
        throw new IllegalStateException("Escalated ships migrations for PostgreSQL and MySQL, and this database is "
                + product + ". Set escalated.datasource.migrate=false and create Escalated's tables another way.");
    }

    private static List<MigrationVersion> adoptableVersions(Connection connection, String hostHistoryTable)
            throws SQLException {
        List<MigrationVersion> versions = new ArrayList<>();

        if (hostHistoryTable == null
                || !PLAIN_IDENTIFIER.matcher(hostHistoryTable).matches()
                || !tableExists(connection, hostHistoryTable)) {
            return versions;
        }

        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT version, script, success FROM " + hostHistoryTable)) {
            while (rows.next()) {
                if (!SCRIPTS_FORMERLY_IN_THE_HOSTS_HISTORY.contains(rows.getString("script"))) {
                    continue;
                }
                if (!rows.getBoolean("success")) {
                    throw new IllegalStateException(hostHistoryTable + " records a failed run of Escalated's "
                            + rows.getString("script") + ". Repair the host's Flyway history before upgrading.");
                }
                versions.add(MigrationVersion.fromVersion(rows.getString("version")));
            }
        }
        return versions;
    }

    private static void removeFromHostHistory(DataSource dataSource, String hostHistoryTable) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM " + hostHistoryTable + " WHERE script = ?")) {
            for (String script : SCRIPTS_FORMERLY_IN_THE_HOSTS_HISTORY) {
                delete.setString(1, script);
                delete.addBatch();
            }
            delete.executeBatch();
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            log.info("Moved Escalated's migration history from {} to {}", hostHistoryTable, HISTORY_TABLE);
        } catch (SQLException ex) {
            throw new IllegalStateException("Escalated's migrations ran, but their old rows could not be removed from "
                    + hostHistoryTable + "; the host's Flyway will report them as missing until they are", ex);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String escape = metaData.getSearchStringEscape();
        String name = metaData.storesUpperCaseIdentifiers() ? table.toUpperCase(Locale.ROOT) : table;
        String pattern = escape == null ? name : name.replace("_", escape + "_");

        try (ResultSet tables = metaData.getTables(connection.getCatalog(), connection.getSchema(), pattern,
                new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    private static MigrationVersion highest(List<MigrationVersion> versions) {
        MigrationVersion highest = versions.get(0);
        for (MigrationVersion version : versions) {
            if (version.compareTo(highest) > 0) {
                highest = version;
            }
        }
        return highest;
    }

    private static final class Inspection {
        private String vendor;
        private boolean hasHistory;
        private boolean hasTables;
        private List<MigrationVersion> adoptable;
    }
}
