-- MySQL edition; db/escalated/postgresql/V24__create_escalated_plugin_tables.sql
-- is the same migration. Change them together, never one alone.

-- The plugin system's tables, as the shared plugin contract
-- (escalated-developer-context domain-model/plugin-contract.md) has them:
-- escalated_plugins, one row per plugin that has ever been activated, and
-- escalated_plugin_store, everything ctx.store and ctx.config write (config is
-- the row with collection and key '__config__'). data is JSON text. The index
-- lengths (191 + 100 + 191 characters) fit MySQL's index limit.

CREATE TABLE escalated_plugins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slug VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    activated_at TIMESTAMP NULL,
    deactivated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_escalated_plugins_slug UNIQUE (slug)
);

CREATE TABLE escalated_plugin_store (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plugin VARCHAR(191) NOT NULL,
    collection VARCHAR(100) NOT NULL,
    `key` VARCHAR(191) NULL,
    data TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_escalated_plugin_store_lookup ON escalated_plugin_store (plugin, collection, `key`);
