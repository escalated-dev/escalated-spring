package dev.escalated.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One {@code escalated_plugin_store} row: a record a plugin keeps through {@code ctx.store}, or its
 * settings ({@code ctx.config}, collection and key {@code __config__}). {@code plugin} is the
 * plugin's name; {@code data} is JSON text.
 */
@Entity
@Table(name = "escalated_plugin_store")
public class PluginStoreRecord extends BaseEntity {

    @Column(nullable = false, length = 191)
    private String plugin;

    @Column(nullable = false, length = 100)
    private String collection;

    // KEY is reserved on MySQL; Hibernate quotes a backticked name for each database.
    @Column(name = "`key`", length = 191)
    private String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String data = "null";

    public String getPlugin() {
        return plugin;
    }

    public void setPlugin(String plugin) {
        this.plugin = plugin;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
