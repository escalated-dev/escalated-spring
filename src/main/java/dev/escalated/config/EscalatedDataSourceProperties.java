package dev.escalated.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection details for the database Escalated's own tables live on.
 *
 * <p>Leave {@code url} unset — the default — and Escalated shares the host
 * application's {@code DataSource}, {@code EntityManagerFactory} and transaction
 * manager, exactly as it always has. Set it and Escalated gets a persistence
 * unit of its own, which is what makes it usable in a host that partitions its
 * data: a schema shared with a legacy system, a separate reporting store, or
 * simply a database the host would rather not mix support data into.
 *
 * <p>Only {@code url} decides. The remaining values fall back to the host's own
 * {@code spring.datasource.*} so a second database on the same server needs one
 * line of configuration, not five.
 */
@ConfigurationProperties(prefix = "escalated.datasource")
public class EscalatedDataSourceProperties {

    /** JDBC URL. When null or blank, Escalated uses the host's DataSource. */
    private String url;

    private String username;

    private String password;

    private String driverClassName;

    /**
     * Hibernate dialect for the Escalated persistence unit. Optional; Hibernate
     * detects it from the connection when unset.
     */
    private String platform;

    /**
     * {@code spring.jpa.hibernate.ddl-auto} for the Escalated persistence unit
     * only. Defaults to {@code none}: schema management belongs to Flyway, and
     * a stray {@code create-drop} here would drop tables on a database the host
     * may share with something else.
     */
    private String ddlAuto = "none";

    /**
     * Run Escalated's Flyway migrations against this database on startup.
     * Defaults to true, because a dedicated database starts empty and nothing
     * else is going to migrate it.
     */
    private boolean migrate = true;

    public boolean isConfigured() {
        return url != null && !url.isBlank();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getDdlAuto() {
        return ddlAuto;
    }

    public void setDdlAuto(String ddlAuto) {
        this.ddlAuto = ddlAuto;
    }

    public boolean isMigrate() {
        return migrate;
    }

    public void setMigrate(boolean migrate) {
        this.migrate = migrate;
    }
}
