package dev.escalated.security;

import dev.escalated.config.EscalatedProperties;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class EscalatedSecurityConfig {

    private final ApiTokenAuthenticationFilter apiTokenFilter;
    private final EscalatedAuthorization authorization;
    private final TicketVisibilityFilter ticketVisibilityFilter;
    private final EscalatedProperties properties;

    public EscalatedSecurityConfig(
            ApiTokenAuthenticationFilter apiTokenFilter,
            EscalatedAuthorization authorization,
            TicketAccess ticketAccess,
            EscalatedProperties properties) {
        this.ticketVisibilityFilter = new TicketVisibilityFilter(ticketAccess);
        this.apiTokenFilter = apiTokenFilter;
        this.authorization = authorization;
        this.properties = properties;
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
    public FilterRegistrationBean<TicketVisibilityFilter> escalatedVisibilityFilterRegistration() {
        FilterRegistrationBean<TicketVisibilityFilter> registration =
                new FilterRegistrationBean<>(ticketVisibilityFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Plugin webhooks (the plugin contract): open, because the plugin authenticates the sender, and
     * without CSRF or the API token filter, which would turn away a sender's own {@code Bearer}.
     */
    @Bean
    @Order(-1)
    public SecurityFilterChain escalatedPluginWebhookSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/escalated/webhooks/plugins/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Plugin endpoints (the plugin contract).
     *
     * <p>They are called by plugin page scripts in the admin's browser, so they take the session as
     * well as an API token, behind the admin gate; the endpoint's capability is checked by
     * {@code PluginEndpointController}. In place of a CSRF token, a call that changes state must carry
     * {@code X-Requested-With} or {@code X-Inertia}, which another site cannot send without a CORS
     * preflight.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain escalatedPluginEndpointSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/escalated/api/plugins/**")
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(apiTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().access(authorization.admin()));
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain escalatedApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/escalated/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(apiTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        ticketVisibilityFilter,
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/escalated/api/widget/**")
                                        .permitAll()
                                        .requestMatchers("/escalated/api/csat/**")
                                        .permitAll()
                                        .requestMatchers("/escalated/api/guest/**")
                                        .permitAll()
                                        // The JSON auth endpoints carry tokens the host issued
                                        // and check them through the host's
                                        // EscalatedApiAuthenticator, answering 401 themselves.
                                        .requestMatchers("/escalated/api/v1/auth/**")
                                        .permitAll()
                                        // Mobile guests hold a grant or an emailed code, never a principal.
                                        .requestMatchers("/escalated/api/v1/mobile/guest/**")
                                        .permitAll()
                                        // Being logged in is not being staff. The same two
                                        // gates as every other Escalated host: admin, and
                                        // agent-or-admin.
                                        .requestMatchers("/escalated/api/admin/**")
                                        .access(authorization.admin())
                                        .requestMatchers("/escalated/api/agent/**")
                                        .access(authorization.agent())
                                        .anyRequest()
                                        .authenticated());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain escalatedWebSecurityFilterChain(HttpSecurity http) throws Exception {
        boolean ui = properties.getUi().isEnabled();
        http.securityMatcher("/escalated/**")
                .csrf(
                        csrf -> {
                            csrf.ignoringRequestMatchers(
                                    "/escalated/api/**",
                                    "/escalated/n/**",
                                    "/escalated/webhook/**",
                                    "/escalated/webhooks/newsletter/**");
                            if (ui) {
                                // Inertia's client (axios) echoes the XSRF-TOKEN cookie back as
                                // X-XSRF-TOKEN, unmasked, as on Laravel; the pages' fetch calls
                                // send the csrf-token meta tag as X-CSRF-TOKEN.
                                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                        .csrfTokenRequestHandler(new InertiaCsrfTokenRequestHandler());
                            }
                        })
                .addFilterBefore(
                        ticketVisibilityFilter,
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .authorizeHttpRequests(
                        auth -> {
                            if (ui) {
                                // The pages behind escalated.ui.enabled: the same two gates as
                                // the admin and agent APIs.
                                auth.requestMatchers("/escalated/admin", "/escalated/admin/**")
                                        .access(authorization.admin())
                                        // The guest pages: no sign-in, a verified email or a grant instead.
                                        .requestMatchers("/escalated/guest", "/escalated/guest/**")
                                        .permitAll()
                                        .requestMatchers("/escalated/agent", "/escalated/agent/**")
                                        .access(authorization.agent());
                            }
                            auth.requestMatchers("/escalated/ws/**")
                                    .permitAll()
                                    .requestMatchers("/escalated/kb/**")
                                    .permitAll()
                                    .requestMatchers("/escalated/n/**")
                                    .permitAll()
                                    // Mail providers have no session and no CSRF token.
                                    // InboundEmailController authenticates them by the
                                    // shared-secret header, and refuses everything while
                                    // no secret is configured.
                                    .requestMatchers("/escalated/webhook/**")
                                    .permitAll()
                                    .requestMatchers("/escalated/webhooks/newsletter/**")
                                    .permitAll()
                                    .anyRequest()
                                    .authenticated();
                        });
        return http.build();
    }
}
