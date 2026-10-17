package com.codepath.worker;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Separate scan boundary: no API controllers, demo seeder, JMS listeners or AI jobs.
@SpringBootApplication(scanBasePackages = {"com.codepath.academy.events", "com.codepath.worker"})
public class WorkerApplication {
    @Bean SecurityFilterChain workerSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/actuator/prometheus")
                .permitAll().anyRequest().denyAll()).build();
    }
}
