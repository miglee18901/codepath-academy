package com.codepath.academy.security;

import com.codepath.academy.config.ManagementSecurityConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ManagementSecurityTest.Config.class)
class ManagementSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity @Import(ManagementSecurityConfig.class)
    static class Config {
        @Bean Endpoints endpoints() { return new Endpoints(); }
        @Bean @Order(3) SecurityFilterChain application(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a -> a.anyRequest().denyAll()).build();
        }
    }
    @RestController static class Endpoints {
        @GetMapping({"/actuator/health", "/actuator/prometheus", "/actuator/env", "/api/categories"})
        String endpoint() { return "ok"; }
    }
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void metricsAreAccessibleOnlyOnManagementPort() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(r -> { r.setLocalPort(9091); return r; }))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus").with(r -> { r.setLocalPort(8080); return r; }))
                .andExpect(status().isForbidden());
    }
    @Test void managementPortDoesNotExposeOtherEndpoints() throws Exception {
        for (String path : new String[]{"/actuator/env", "/api/categories"}) {
            mvc.perform(get(path).with(r -> { r.setLocalPort(9091); return r; }))
                    .andExpect(status().isForbidden());
        }
    }
}
