package dev.escalated.config;

import dev.escalated.controllers.widget.GuestThrottleInterceptor;
import dev.escalated.controllers.widget.WidgetAvailabilityInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the widget on/off switch (widget routes only) and the guest
 * endpoint rate limit. The rate limit acts only on handlers marked
 * {@code @GuestThrottle}, so every other request passes straight through.
 */
@Configuration(proxyBeanMethods = false)
public class GuestThrottleWebConfig implements WebMvcConfigurer {

    private final GuestThrottleInterceptor guestThrottleInterceptor;
    private final WidgetAvailabilityInterceptor widgetAvailabilityInterceptor;

    public GuestThrottleWebConfig(GuestThrottleInterceptor guestThrottleInterceptor,
                                  WidgetAvailabilityInterceptor widgetAvailabilityInterceptor) {
        this.guestThrottleInterceptor = guestThrottleInterceptor;
        this.widgetAvailabilityInterceptor = widgetAvailabilityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // A disabled widget refuses before anything is counted or created.
        registry.addInterceptor(widgetAvailabilityInterceptor).addPathPatterns("/escalated/api/widget/**");
        registry.addInterceptor(guestThrottleInterceptor);
    }
}
