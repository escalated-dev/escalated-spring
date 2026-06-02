package dev.escalated.config;

import dev.escalated.services.newsletter.NewsletterRenderer;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
@ConditionalOnProperty(prefix = "escalated.newsletters", name = "enabled", havingValue = "true")
public class NewsletterConfiguration {

    @Bean
    public NewsletterRenderer newsletterRenderer(
            EscalatedProperties properties,
            ResourceLoader resourceLoader,
            @Value("${spring.application.name:Support}") String appName) {
        EscalatedProperties.NewslettersProperties newsletters = properties.getNewsletters();
        NewsletterRenderer.Options opts = new NewsletterRenderer.Options();
        opts.baseUrl = newsletters.getAppUrl();
        opts.defaultTheme = newsletters.getDefaultTheme();
        opts.trackingEnabled = newsletters.isTrackingEnabled();
        opts.brandAccent = newsletters.getBrandAccent();
        opts.brandLogoUrl = newsletters.getBrandLogoUrl();
        opts.brandPhysicalAddress = newsletters.getBrandPhysicalAddress();
        opts.brandName = appName;

        String configured = newsletters.getThemesDir();
        if (configured != null && !configured.isBlank()) {
            opts.themesDir = configured;
        } else {
            try {
                opts.themesDir = Path.of(
                                resourceLoader
                                        .getResource("classpath:templates/escalated/newsletter_themes/")
                                        .getURI())
                        .toString();
            } catch (Exception ex) {
                opts.themesDir = "templates/escalated/newsletter_themes";
            }
        }
        return new NewsletterRenderer(opts);
    }
}
