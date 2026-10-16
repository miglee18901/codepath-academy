package com.codepath.academy.security;

import com.codepath.academy.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.util.UriComponentsBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SocialOAuthFlowTest.Config.class)
@TestPropertySource(properties = "app.frontend-url=http://localhost:5173")
class SocialOAuthFlowTest {
    @Configuration @EnableWebMvc @EnableWebSecurity @Import(SocialOAuthConfig.class)
    static class Config {
        @Bean SocialClientRegistrations clients() {
            return new SocialClientRegistrations(new MockEnvironment()
                    .withProperty("app.oauth.google.client-id", "test-google-id")
                    .withProperty("app.oauth.google.client-secret", "test-google-secret")
                    .withProperty("app.oauth.github.client-id", "test-github-id")
                    .withProperty("app.oauth.github.client-secret", "test-github-secret"));
        }
        @Bean OAuthHandoffService handoff() { return mock(OAuthHandoffService.class); }
        @Bean GithubOAuthUserService github() { return mock(GithubOAuthUserService.class); }
        @Bean SocialLoginHandler handler(OAuthHandoffService handoff) {
            var handler = new SocialLoginHandler(handoff);
            ReflectionTestUtils.setField(handler, "frontendUrl", "http://localhost:5173");
            return handler;
        }
    }

    @Autowired WebApplicationContext context;
    private MockMvc mvc;
    @BeforeEach void setup() { mvc = webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void googleAuthorizationUsesExactCallbackStateNonceAndPkce() throws Exception {
        var result = mvc.perform(get("/api/auth/oauth2/authorization/google").param("challenge", "A".repeat(43)))
                .andExpect(status().isFound()).andReturn();
        var uri = UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build();
        assertEquals("accounts.google.com", uri.getHost());
        var query = uri.getQueryParams();
        assertEquals("S256", query.getFirst("code_challenge_method"));
        assertNotNull(query.getFirst("state")); assertNotNull(query.getFirst("nonce"));
        assertEquals("http://localhost:5173/api/auth/oauth2/callback/google",
                java.net.URLDecoder.decode(query.getFirst("redirect_uri"), java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(result.getResponse().getRedirectedUrl().contains("test-google-secret"));
        assertFalse(result.getResponse().getRedirectedUrl().contains("codepath_handoff"));
        assertEquals(300, result.getRequest().getSession(false).getMaxInactiveInterval());
    }

    @Test void githubRequestsPrivateEmailScopeAndPkce() throws Exception {
        var result = mvc.perform(get("/api/auth/oauth2/authorization/github").param("challenge", "A".repeat(43)))
                .andExpect(status().isFound()).andReturn();
        var uri = UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build();
        assertEquals("github.com", uri.getHost());
        assertEquals("S256", uri.getQueryParams().getFirst("code_challenge_method"));
        assertTrue(java.net.URLDecoder.decode(uri.getQueryParams().getFirst("scope"), java.nio.charset.StandardCharsets.UTF_8)
                .contains("user:email"));
    }

    @Test void unsolicitedCallbackFailsWithoutCallingProvider() throws Exception {
        mvc.perform(get("/api/auth/oauth2/callback/google").param("code", "fake").param("state", "fake"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/oauth/callback#error=social_failed"));
    }

    @Test void validStateWithProviderCancellationClearsTransientSession() throws Exception {
        var start = mvc.perform(get("/api/auth/oauth2/authorization/google").param("challenge", "A".repeat(43)))
                .andReturn();
        var session = (MockHttpSession) start.getRequest().getSession(false);
        var authorization = (OAuth2AuthorizationRequest) session.getAttribute(
                "org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST");
        assertNotNull(authorization);
        mvc.perform(get("/api/auth/oauth2/callback/google").session(session)
                        .param("state", authorization.getState()).param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/oauth/callback#error=access_denied"));
        assertTrue(session.isInvalid());
    }
}
