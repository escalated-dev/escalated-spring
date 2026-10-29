package dev.escalated.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/** Slugs, route validation and the boot-time checks on registered plugins. */
class PluginRegistrationTest {

    private static EscalatedPlugin named(String name, List<PluginRoute> endpoints) {
        return new EscalatedPlugin() {
            @Override
            public PluginManifest manifest() {
                return PluginManifest.of(name, "1.0.0");
            }

            @Override
            public List<PluginRoute> endpoints() {
                return endpoints;
            }
        };
    }

    private static PluginRegistry registry(EscalatedPlugin... plugins) {
        return new PluginRegistry(List.of(plugins), null, null, null, (user, capability) -> true, user -> true, null,
                Executors.newSingleThreadExecutor());
    }

    @Test
    void slugsFollowTheContract() {
        assertThat(PluginSlugs.slug("@escalated-dev/plugin-slack")).isEqualTo("escalated-dev--plugin-slack");
        assertThat(PluginSlugs.slug("acme/billing")).isEqualTo("acme--billing");
        assertThat(PluginSlugs.slug("my plugin.v2")).isEqualTo("my-plugin-v2");
    }

    @Test
    void twoPluginsWithOneSlugFailAtBootNamingBoth() {
        assertThatThrownBy(() -> registry(named("acme/billing", List.of()), named("@acme/billing", List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acme/billing").hasMessageContaining("@acme/billing");
    }

    @Test
    void aMalformedRouteOrAPluginWithoutANameFailsAtBoot() {
        for (String path : List.of("no-slash", "/a/../b", "/a?b", "/a b")) {
            assertThatThrownBy(() -> registry(named("acme/x", List.of(PluginRoute.of("GET", path, (r, c) -> null)))))
                    .as(path).isInstanceOf(IllegalStateException.class).hasMessageContaining("acme/x");
        }
        assertThatThrownBy(() -> registry(named("acme/x", List.of(PluginRoute.of("FETCH", "/a", (r, c) -> null)))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry(named(" ", List.of()))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void routesMatchParamsOnTheDeclaredPath() {
        ParsedRoute route = ParsedRoute.parse("acme/x", "get", "/items/:id/lines/:line", null, (r, c) -> null);
        assertThat(route.match("GET", "/items/4%2F2/lines/7")).isEqualTo(Map.of("id", "4/2", "line", "7"));
        assertThat(route.match("POST", "/items/4/lines/7")).isNull();
        assertThat(route.match("GET", "/items//lines/7")).isNull();
        assertThat(route.match("GET", "/items/4/lines")).isNull();
    }

    @Test
    void anEnvelopeIsValidatedAndAPlainValueIsJson() throws Exception {
        PluginHttp.Reply plain = PluginHttp.reply(Map.of("ok", true));
        assertThat(plain.status()).isEqualTo(200);
        assertThat(new String(plain.body())).isEqualTo("{\"ok\":true}");

        PluginHttp.Reply text = PluginHttp.reply(Map.of("__escalated_http", 1, "status", 202, "format", "text",
                "headers", Map.of("X-A", "b"), "body", "done"));
        assertThat(text.status()).isEqualTo(202);
        assertThat(text.headers()).containsEntry("x-a", "b").containsEntry("content-type", "text/plain;charset=UTF-8");

        for (Map<String, Object> bad : List.<Map<String, Object>>of(
                Map.of("__escalated_http", 2, "status", 200, "format", "json", "headers", Map.of(), "body", 1),
                Map.of("__escalated_http", 1, "status", 99, "format", "json", "headers", Map.of(), "body", 1),
                Map.of("__escalated_http", 1, "status", 200, "format", "xml", "headers", Map.of(), "body", 1),
                Map.of("__escalated_http", 1, "status", 200, "format", "json", "headers", Map.of("Connection", "x"),
                        "body", 1),
                Map.of("__escalated_http", 1, "status", 200, "format", "text", "headers", Map.of(), "body", 1),
                Map.of("__escalated_http", 1, "status", 200, "format", "json", "headers", Map.of()))) {
            assertThatThrownBy(() -> PluginHttp.reply(bad)).as(bad.toString())
                    .isInstanceOf(PluginHttp.InvalidResponseException.class);
        }
    }
}
