package dev.escalated.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class EscalatedSecurityConfig {

    private final ApiTokenAuthenticationFilter apiTokenFilter;
    private final EscalatedAuthorization authorization;

    public EscalatedSecurityConfig(ApiTokenAuthenticationFilter apiTokenFilter,
                                   EscalatedAuthorization authorization) {
        this.apiTokenFilter = apiTokenFilter;
        this.authorization = authorization;
    }

    /**
     * Keeps the token filter out of the servlet container's filter chain.
     *
     * <p>Boot registers every {@code Filter} bean on {@code /*} unless told
     * otherwise. This one belongs inside the API security chain below and
     * nowhere else; registered globally it ran on every request the host
     * served and answered the host's own {@code Bearer} routes with 401.
     */
    @Bean
    public FilterRegistrationBean<ApiTokenAuthenticationFilter> escalatedApiTokenFilterRegistration() {
        FilterRegistrationBean<ApiTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(apiTokenFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain escalatedApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/escalated/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(apiTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/escalated/api/widget/**").permitAll()
                        .requestMatchers("/escalated/api/csat/**").permitAll()
                        .requestMatchers("/escalated/api/guest/**").permitAll()
                        // The JSON auth endpoints carry tokens the host issued
                        // and check them through the host's
                        // EscalatedApiAuthenticator, answering 401 themselves.
                        .requestMatchers("/escalated/api/v1/auth/**").permitAll()
                        // Being logged in is not being staff. The same two
                        // gates as every other Escalated host: admin, and
                        // agent-or-admin.
                        .requestMatchers("/escalated/api/admin/**").access(authorization.admin())
                        .requestMatchers("/escalated/api/agent/**").access(authorization.agent())
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain escalatedWebSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/escalated/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/escalated/api/**",
                        "/escalated/n/**",
                        "/escalated/webhook/**",
                        "/escalated/webhooks/newsletter/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/escalated/ws/**").permitAll()
                        .requestMatchers("/escalated/kb/**").permitAll()
                        .requestMatchers("/escalated/n/**").permitAll()
                        // Mail providers have no session and no CSRF token.
                        // InboundEmailController authenticates them by the
                        // shared-secret header, and refuses everything while
                        // no secret is configured.
                        .requestMatchers("/escalated/webhook/**").permitAll()
                        .requestMatchers("/escalated/webhooks/newsletter/**").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
