package dev.escalated.security;

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

    public EscalatedSecurityConfig(ApiTokenAuthenticationFilter apiTokenFilter) {
        this.apiTokenFilter = apiTokenFilter;
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
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain escalatedWebSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/escalated/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers("/escalated/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/escalated/ws/**").permitAll()
                        .requestMatchers("/escalated/kb/**").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
