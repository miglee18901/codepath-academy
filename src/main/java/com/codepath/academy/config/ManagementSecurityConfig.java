package com.codepath.academy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ManagementSecurityConfig {
    @Bean
    @Order(2)
    SecurityFilterChain managementSecurity(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(request -> request.getLocalPort() == 9091)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
