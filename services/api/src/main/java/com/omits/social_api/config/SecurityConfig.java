package com.omits.social_api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
public class SecurityConfig {

    @Bean
    public ApiKeyAuthFilter apiKeyAuthFilter(ApiKeyProperties apiKeyProperties) {
        return new ApiKeyAuthFilter(apiKeyProperties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
        http.csrf(CsrfConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/health", "/actuator/health").permitAll()
                        // Spring forwards an unhandled exception to /error as an ERROR
                        // dispatch, which OncePerRequestFilter skips by default — so
                        // apiKeyAuthFilter does not re-run and the context is empty. Without
                        // this, every unhandled exception reports as 401 instead of its own
                        // status, which hides the real failure from callers.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(apiKeyAuthFilter, AuthorizationFilter.class);
        return http.build();
    }
}
