package com.codepath.academy.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    private CorsConfiguration cors(String frontendUrl) {
        return cors(frontendUrl, "");
    }

    private CorsConfiguration cors(String frontendUrl, String extraOrigins) {
        SecurityConfig config = new SecurityConfig(null, null, null, null);
        ReflectionTestUtils.setField(config, "frontendUrl", frontendUrl);
        ReflectionTestUtils.setField(config, "additionalCorsOrigins", extraOrigins);
        return config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/courses"));
    }

    @Test
    void localFrontendAcceptsBothLoopbackNamesOnlyOnConfiguredPort() {
        for (String host : new String[]{"localhost", "127.0.0.1"}) {
            CorsConfiguration config = cors("http://" + host + ":5173");
            assertNotNull(config.checkOrigin("http://localhost:5173"));
            assertNotNull(config.checkOrigin("http://127.0.0.1:5173"));
            assertNull(config.checkOrigin("http://localhost:3000"));
            assertNull(config.checkOrigin("https://untrusted.example"));
        }
    }

    @Test
    void deployedFrontendAcceptsOnlyConfiguredOrigin() {
        CorsConfiguration config = cors("https://academy.example");
        assertNotNull(config.checkOrigin("https://academy.example"));
        assertNull(config.checkOrigin("http://localhost:5173"));
        assertNull(config.checkOrigin("https://untrusted.example"));
    }

    @Test
    void tunnelCanExplicitlyAllowLocalFrontendWithoutAllowingOtherOrigins() {
        CorsConfiguration config = cors("https://academy.ngrok-free.dev",
                " http://localhost:5173, http://127.0.0.1:5173, ");
        assertNotNull(config.checkOrigin("https://academy.ngrok-free.dev"));
        assertNotNull(config.checkOrigin("http://localhost:5173"));
        assertNotNull(config.checkOrigin("http://127.0.0.1:5173"));
        assertNull(config.checkOrigin("http://localhost:3000"));
        assertNull(config.checkOrigin("https://untrusted.example"));
    }
}
