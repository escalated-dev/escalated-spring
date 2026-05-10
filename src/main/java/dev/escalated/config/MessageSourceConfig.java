package dev.escalated.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;

/**
 * Wires the helpdesk's {@link MessageSource} so translation strings come from
 * the central <code>dev.escalated:escalated-locale</code> Maven artifact, with
 * an optional local override layer.
 *
 * <p>Resolution order (first match wins):
 * <ol>
 *   <li><code>classpath:i18n/overrides/messages</code> -- host-app overrides
 *       checked into <code>src/main/resources/i18n/overrides/</code>.</li>
 *   <li><code>classpath:META-INF/escalated/locale/messages</code> -- shipped by
 *       the central <code>escalated-locale</code> artifact.</li>
 * </ol>
 *
 * <p>The central artifact is assumed to package its bundles under
 * <code>META-INF/escalated/locale/messages_{locale}.properties</code>. This
 * mirrors the convention used by other Escalated host plugins and avoids
 * collisions with the host app's own <code>messages.properties</code>.
 */
@Configuration
public class MessageSourceConfig {

    /**
     * Central artifact basename. The artifact ships its bundles at
     * <code>META-INF/escalated/locale/messages_{locale}.properties</code>.
     */
    private static final String CENTRAL_BASENAME =
            "classpath:META-INF/escalated/locale/messages";

    /**
     * Local override basename. Drop a <code>messages_{locale}.properties</code>
     * file under <code>src/main/resources/i18n/overrides/</code> in the host
     * app to override individual keys without forking the central artifact.
     */
    private static final String OVERRIDE_BASENAME =
            "classpath:i18n/overrides/messages";

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source =
                new ReloadableResourceBundleMessageSource();
        // Order matters: overrides resolved before the central bundle.
        source.setBasenames(OVERRIDE_BASENAME, CENTRAL_BASENAME);
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        // Cache forever; translations are immutable jar resources.
        source.setCacheSeconds(-1);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }
}
