package com.codepath.academy.security;

import java.util.*;
import java.net.URI;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.cors.allowed-origins:}")
    private String additionalCorsOrigins = "";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider)
                .exceptionHandling(e ->
                        e
                                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                                .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .authorizeHttpRequests(a ->
                        a
                                .requestMatchers(HttpMethod.OPTIONS, "/**")
                                .permitAll()
                                .requestMatchers("/api/auth/**", "/api/payments/webhook", "/error")
                                .permitAll()
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/courses",
                                        "/api/courses/*",
                                        "/api/categories",
                                        "/api/ai/capabilities",
                                        "/api/search/courses",
                                        "/api/lessons/*",
                                        "/api/lessons/*/playback"
                                )
                                .permitAll()
                                .requestMatchers("/api/admin/**")
                                .hasRole("ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/courses/*/assistant")
                                .authenticated()
                                .requestMatchers("/api/me/**", "/api/orders/**")
                                .authenticated()
                                .anyRequest()
                                .denyAll()
                )
                .addFilterBefore(
                        jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        Set<String> origins = new LinkedHashSet<>(frontendOrigins());
        Arrays.stream(additionalCorsOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .forEach(origins::add);
        c.setAllowedOrigins(new ArrayList<>(origins));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept-Language")
        );
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }

    private List<String> frontendOrigins() {
        URI origin = URI.create(frontendUrl);
        String host = origin.getHost();
        if (!"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host)) {
            return List.of(frontendUrl);
        }
        String alias = "localhost".equalsIgnoreCase(host) ? "127.0.0.1" : "localhost";
        String port = origin.getPort() == -1 ? "" : ":" + origin.getPort();
        return List.of(frontendUrl, origin.getScheme() + "://" + alias + port);
    }
}
