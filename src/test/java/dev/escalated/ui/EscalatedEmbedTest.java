package dev.escalated.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.escalated.config.EmbedEnvironmentPostProcessor;
import dev.escalated.config.EscalatedProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** The embed settings and path arithmetic, without a context. */
class EscalatedEmbedTest {

    private static EscalatedEmbed embed(EscalatedEmbedConfigurer configurer) {
        return new EscalatedEmbed(new EscalatedProperties(), List.of(configurer));
    }

    @Test
    void offByDefault() {
        EscalatedEmbed embed = new EscalatedEmbed(new EscalatedProperties());
        assertThat(embed.enabled()).isFalse();
        assertThat(embed.prefix()).isEqualTo("admin/support");
        assertThat(embed.mode()).isEqualTo("layout");
        assertThat(embed.middleware()).isEmpty();
        assertThat(embed.replacesAdminGate()).isFalse();
        assertThat(embed.frameAncestors()).isEmpty();
        assertThat(embed.toStandalone("/admin/support/tickets")).isEqualTo("/admin/support/tickets");
        assertThat(embed.rewriteLocation("/escalated/admin/tickets", "")).isEqualTo("/escalated/admin/tickets");
    }

    @Test
    void theConfigurerIsTheEmbedAdminCall() {
        EscalatedEmbed embed = embed(config -> config.embedAdmin()
                .prefix("/backoffice/helpdesk/")
                .middleware("hostAdminFilter")
                .layout("Layouts/AdminLayout")
                .rootView("host/admin")
                .mode("iframe")
                .replaceAdminGate(true)
                .frameAncestors("https://admin.example.com"));
        assertThat(embed.enabled()).isTrue();
        assertThat(embed.basePath()).isEqualTo("/backoffice/helpdesk");
        assertThat(embed.middleware()).containsExactly("hostAdminFilter");
        assertThat(embed.layout()).isEqualTo("Layouts/AdminLayout");
        assertThat(embed.rootView()).isEqualTo("host/admin");
        assertThat(embed.iframe()).isTrue();
        assertThat(embed.replacesAdminGate()).isTrue();
        assertThat(embed.frameAncestorsPolicy()).isEqualTo("frame-ancestors 'self' https://admin.example.com");
    }

    @Test
    void pathsAndRedirectsMoveExceptTwoFactor() {
        EscalatedEmbed embed = embed(config -> config.embedAdmin());
        assertThat(EscalatedEmbed.moves("/escalated/admin")).isTrue();
        assertThat(EscalatedEmbed.moves("/escalated/admin/tickets/5")).isTrue();
        assertThat(EscalatedEmbed.moves("/escalated/administrator")).isFalse();
        assertThat(EscalatedEmbed.moves("/escalated/admin/settings/two-factor")).isFalse();
        assertThat(EscalatedEmbed.moves("/escalated/admin/settings/two-factor/setup")).isFalse();
        assertThat(EscalatedEmbed.moves("/escalated/agent")).isFalse();

        assertThat(embed.toStandalone("/admin/support")).isEqualTo("/escalated/admin");
        assertThat(embed.toStandalone("/admin/support/tags/3")).isEqualTo("/escalated/admin/tags/3");
        assertThat(embed.toEmbedded("/escalated/admin/tags/3")).isEqualTo("/admin/support/tags/3");

        assertThat(embed.rewriteLocation("/escalated/admin/tags?page=2#top", ""))
                .isEqualTo("/admin/support/tags?page=2#top");
        assertThat(embed.rewriteLocation("https://help.example/app/escalated/admin/tags", "/app"))
                .isEqualTo("https://help.example/app/admin/support/tags");
        assertThat(embed.rewriteLocation("/escalated/admin/settings/two-factor", ""))
                .isEqualTo("/escalated/admin/settings/two-factor");
        assertThat(embed.rewriteLocation("/escalated/agent/tickets", "")).isEqualTo("/escalated/agent/tickets");
        assertThat(embed.rewriteLocation("https://elsewhere.example/escalated/admin", "/app"))
                .isEqualTo("https://elsewhere.example/escalated/admin");
    }

    @Test
    void theRouteNameOfAPath() {
        UiRoutes routes = new UiRoutes();
        routes.mountAdminAt("/admin/support");
        assertThat(routes.nameOf("/admin/support/tickets")).isEqualTo("escalated.admin.tickets.index");
        assertThat(routes.nameOf("/admin/support/tickets/42")).isEqualTo("escalated.admin.tickets.show");
        assertThat(routes.nameOf("/admin/support/workflows/logs")).isEqualTo("escalated.admin.workflows.logs");
        assertThat(routes.nameOf("/admin/support/departments/3/edit")).isEqualTo("escalated.admin.departments.edit");
        assertThat(routes.nameOf("/nowhere")).isNull();
    }

    @Test
    void theEnvironmentVariablesAreRead() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("env", Map.of(
                "ESCALATED_EMBED", "true",
                "ESCALATED_EMBED_MODE", "iframe",
                "ESCALATED_EMBED_PREFIX", "backoffice/support",
                "ESCALATED_EMBED_FRAME_ANCESTORS", "https://a.example,https://b.example")));
        new EmbedEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
        EscalatedProperties.EmbedProperties bound = Binder.get(environment)
                .bind("escalated", EscalatedProperties.class).get().getUi().getEmbed();
        assertThat(bound.isEnabled()).isTrue();
        assertThat(bound.getMode()).isEqualTo("iframe");
        assertThat(bound.getPrefix()).isEqualTo("backoffice/support");
        assertThat(bound.getFrameAncestors()).containsExactly("https://a.example", "https://b.example");

        // A key set any other way wins over the variable.
        environment.getPropertySources().addFirst(new MapPropertySource("app", Map.of(
                "escalated.ui.embed.prefix", "admin/help")));
        assertThat(Binder.get(environment).bind("escalated", EscalatedProperties.class).get()
                .getUi().getEmbed().getPrefix()).isEqualTo("admin/help");
    }

    @Test
    void theEmbedFiltersExistOnlyWhenEmbeddingIsConfigured() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Probe.class);
        runner.run(context -> assertThat(context).doesNotHaveBean("embedProbe"));
        runner.withPropertyValues("escalated.ui.embed.enabled=true")
                .run(context -> assertThat(context).hasBean("embedProbe"));
        runner.withBean(EscalatedEmbedConfigurer.class, () -> config -> config.embedAdmin())
                .run(context -> assertThat(context).hasBean("embedProbe"));
    }

    /**
     * Stands in for the embed filters' registrations, which carry the same condition. Not a
     * {@code @Configuration}: Escalated's component scan would register it in every context.
     */
    static class Probe {
        @Bean
        @Conditional(EmbedEnabledCondition.class)
        String embedProbe() {
            return "registered";
        }
    }

    @Test
    void nothingIsReadWithoutTheVariables() {
        StandardEnvironment environment = new StandardEnvironment();
        new EmbedEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getProperty("escalated.ui.embed.enabled")).isNull();
    }
}
