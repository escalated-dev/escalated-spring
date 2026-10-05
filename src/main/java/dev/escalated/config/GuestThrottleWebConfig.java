package dev.escalated.config;

import dev.escalated.controllers.widget.GuestThrottleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the guest endpoint rate limit. It acts only on handlers marked
 * {@code @GuestThrottle}, so every other request passes straight through.
 */
@Configuration(proxyBeanMethods = false)
public class GuestThrottleWebConfig implements WebMvcConfigurer {

    private final GuestThrottleInterceptor guestThrottleInterceptor;

    public GuestThrottleWebConfig(GuestThrottleInterceptor guestThrottleInterceptor) {
        this.guestThrottleInterceptor = guestThrottleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guestThrottleInterceptor);
    }
}
