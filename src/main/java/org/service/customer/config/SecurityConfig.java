package org.service.customer.config;

import org.service.customer.filters.CustomTenantFilter;
import org.service.customer.filters.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomTenantFilter customTenantFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, CustomTenantFilter customTenantFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.customTenantFilter = customTenantFilter;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Use this syntax to disable CSRF in the new version
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/tenants/{tenantId}/users/register").permitAll()  // Public access
                        .requestMatchers("/api/v1/tenants/{tenantId}/users/login").permitAll()
                        .requestMatchers("/api/v1/tenants/{tenantId}/users/**").hasRole("ADMIN")  // Restrict to ADMIN role
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(customTenantFilter, UsernamePasswordAuthenticationFilter.class) // Register custom filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }



}
