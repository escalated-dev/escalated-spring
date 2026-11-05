package dev.escalated.config;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * Reads the embeddable admin's environment variables (ADR 2026-11-05-embeddable-admin), the names
 * every Escalated host uses: {@code ESCALATED_EMBED}, {@code ESCALATED_EMBED_MODE},
 * {@code ESCALATED_EMBED_PREFIX} and {@code ESCALATED_EMBED_FRAME_ANCESTORS} (comma-separated)
 * stand for {@code escalated.ui.embed.enabled}, {@code .mode}, {@code .prefix} and
 * {@code .frame-ancestors}.
 *
 * <p>Looked up last, so any other setting of those keys (properties, YAML, Boot's own
 * {@code ESCALATED_UI_EMBED_*} relaxed binding) wins.
 */
public class EmbedEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE = "escalatedEmbedEnvironment";

    static final Map<String, String> KEYS = Map.of(
            "escalated.ui.embed.enabled", "ESCALATED_EMBED",
            "escalated.ui.embed.mode", "ESCALATED_EMBED_MODE",
            "escalated.ui.embed.prefix", "ESCALATED_EMBED_PREFIX",
            "escalated.ui.embed.frame-ancestors", "ESCALATED_EMBED_FRAME_ANCESTORS");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getPropertySources().contains(SOURCE)) {
            environment.getPropertySources().addLast(new Source(environment));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Answers each embed key with its environment variable, looked up when it is asked for. The
     * environment is held aside rather than as the source: Boot walks a source that is an
     * environment as nested sources, which would include this one again.
     */
    static final class Source extends PropertySource<Object> {

        private final ConfigurableEnvironment environment;

        Source(ConfigurableEnvironment environment) {
            super(SOURCE, new Object());
            this.environment = environment;
        }

        @Override
        public Object getProperty(String name) {
            String variable = KEYS.get(name);
            if (variable == null) {
                return null;
            }
            String value = environment.getProperty(variable);
            return value == null || value.isBlank() ? null : value;
        }
    }
}
