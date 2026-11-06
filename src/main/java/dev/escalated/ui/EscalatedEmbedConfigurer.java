package dev.escalated.ui;

import dev.escalated.config.EscalatedProperties;

/**
 * Sets {@code escalated.ui.embed} from Java, the {@code embedAdmin(...)} call of the other
 * Escalated hosts (ADR 2026-11-05-embeddable-admin). A host declares one as a bean:
 *
 * <pre>{@code
 * @Bean
 * EscalatedEmbedConfigurer escalatedEmbed() {
 *     return embed -> embed.embedAdmin()
 *             .prefix("admin/support")
 *             .middleware("hostAdminFilter")
 *             .layout("Layouts/AdminLayout");
 * }
 * }</pre>
 *
 * <p>Applied over the bound properties when the context starts. Equivalent to setting the keys.
 */
@FunctionalInterface
public interface EscalatedEmbedConfigurer {

    void configure(EscalatedProperties.EmbedProperties embed);
}
