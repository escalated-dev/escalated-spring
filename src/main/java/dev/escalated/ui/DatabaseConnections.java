package dev.escalated.ui;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * The admin Database page, read-only: each configured {@link DataSource} bean described as
 * escalated-laravel's {@code ConnectionInspector} does ({@code name, driver, database, is_current,
 * is_host_default, reachable, migrated, ticket_count, error}). Escalated's tables live on
 * {@code escalatedDataSource} when {@code escalated.datasource.url} is set, else on the host's
 * {@code dataSource}; either way that is configuration, so the page is pinned and nothing switches
 * from here. A probe that fails is reported, never thrown, and no credentials are shown.
 */
@Service
@ConditionalOnProperty(prefix = "escalated.ui", name = "enabled", havingValue = "true")
public class DatabaseConnections {

    /** The bean of Escalated's own database, when the host gave it one. */
    public static final String DEDICATED = "escalatedDataSource";

    /** The host's database, Spring Boot's bean name for it. */
    public static final String HOST = "dataSource";

    public static final String TABLE_PREFIX = "escalated_";

    private final Map<String, DataSource> dataSources;

    public DatabaseConnections(Map<String, DataSource> dataSources) {
        this.dataSources = new LinkedHashMap<>(dataSources);
    }

    /** The connection Escalated's tables are on. */
    public String current() {
        if (dataSources.containsKey(DEDICATED)) {
            return DEDICATED;
        }
        return hostDefault();
    }

    /** The host's own connection. */
    public String hostDefault() {
        if (dataSources.containsKey(HOST)) {
            return HOST;
        }
        return dataSources.keySet().stream().filter(name -> !DEDICATED.equals(name)).findFirst()
                .orElse(dataSources.keySet().stream().findFirst().orElse(null));
    }

    public boolean isConfigured(String name) {
        return name != null && dataSources.containsKey(name);
    }

    public Map<String, Object> props() {
        List<Map<String, Object>> connections = new ArrayList<>();
        for (String name : dataSources.keySet()) {
            connections.add(describe(name));
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("connections", connections);
        props.put("current", current());
        props.put("hostDefault", hostDefault());
        props.put("pinnedByConfig", true);
        props.put("tablePrefix", TABLE_PREFIX);
        return props;
    }

    public Map<String, Object> describe(String name) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("name", name);
        description.put("driver", null);
        description.put("database", null);
        description.put("is_current", name.equals(current()));
        description.put("is_host_default", name.equals(hostDefault()));
        description.put("reachable", false);
        description.put("migrated", false);
        description.put("ticket_count", null);
        description.put("error", null);

        DataSource dataSource = dataSources.get(name);
        if (dataSource == null) {
            description.put("error", "[" + name + "] is not a configured connection.");
            return description;
        }
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            description.put("driver", driver(metadata.getDatabaseProductName()));
            description.put("database", label(metadata.getURL()));
            description.put("reachable", connection.isValid(5));
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(5);
                try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + TABLE_PREFIX + "tickets")) {
                    rows.next();
                    description.put("migrated", true);
                    description.put("ticket_count", rows.getLong(1));
                }
            } catch (SQLException noTicketsTable) {
                // Reachable but not migrated; Laravel reports no error for that.
                description.put("migrated", false);
            }
        } catch (SQLException | RuntimeException e) {
            description.put("reachable", false);
            description.put("error", e.getMessage());
        }
        return description;
    }

    static String driver(String product) {
        if (product == null) {
            return null;
        }
        String lower = product.toLowerCase(Locale.ROOT);
        if (lower.contains("postgres")) {
            return "pgsql";
        }
        if (lower.contains("mysql")) {
            return "mysql";
        }
        if (lower.contains("mariadb")) {
            return "mariadb";
        }
        if (lower.contains("sql server")) {
            return "sqlsrv";
        }
        return lower.replace(' ', '_');
    }

    /**
     * The database a JDBC URL names, without credentials or parameters: {@code "db @ host:port"},
     * or the URL's own name for an embedded database ({@code "mem:testdb"}).
     */
    static String label(String url) {
        if (url == null) {
            return null;
        }
        String rest = url.startsWith("jdbc:") ? url.substring(5) : url;
        int colon = rest.indexOf(':');
        rest = colon < 0 ? rest : rest.substring(colon + 1);
        rest = cut(cut(rest, '?'), ';');
        if (rest.startsWith("//")) {
            rest = rest.substring(2);
            int slash = rest.indexOf('/');
            String host = slash < 0 ? rest : rest.substring(0, slash);
            int at = host.lastIndexOf('@');
            host = at < 0 ? host : host.substring(at + 1);
            String database = slash < 0 ? "" : rest.substring(slash + 1);
            return database.isEmpty() ? host : database + " @ " + host;
        }
        return rest;
    }

    private static String cut(String value, char at) {
        int index = value.indexOf(at);
        return index < 0 ? value : value.substring(0, index);
    }
}
