package dev.escalated.widget;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Puts {@link WidgetCorsFilter} on the widget routes only, ahead of Spring
 * Security, so preflights never reach the security chain and every answer
 * from it (and from the guest rate limit) already carries the CORS grant.
 */
@Configuration(proxyBeanMethods = false)
public class WidgetEmbedConfiguration {

    /**
     * Spring Security's filter runs at -100 by default
     * ({@code spring.security.filter.order}); Boot's forwarded-header filter,
     * which the same-origin check relies on behind a proxy, runs first.
     */
    public static final int FILTER_ORDER = -110;

    @Bean
    public FilterRegistrationBean<WidgetCorsFilter> escalatedWidgetCorsFilter(WidgetOrigins origins) {
        FilterRegistrationBean<WidgetCorsFilter> registration = new FilterRegistrationBean<>(new WidgetCorsFilter(origins));
        registration.setName("escalatedWidgetCorsFilter");
        registration.addUrlPatterns(WidgetCorsFilter.PATH, WidgetCorsFilter.PATH + "/*");
        registration.setOrder(FILTER_ORDER);
        return registration;
    }
}
