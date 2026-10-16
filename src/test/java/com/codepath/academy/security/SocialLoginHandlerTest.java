package com.codepath.academy.security;

import com.codepath.academy.entity.User;
import com.codepath.academy.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialLoginHandlerTest {
    private final OAuthHandoffService handoff = mock(OAuthHandoffService.class);
    private final SocialLoginHandler handler = new SocialLoginHandler(handoff);
    private final List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("OAUTH2_USER"));
    private MockHttpServletRequest request() {
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://localhost:5173");
        var request = new MockHttpServletRequest();
        request.setAttribute(SocialAuthorizationRequests.CHALLENGE, "A".repeat(43));
        request.getSession();
        return request;
    }

    @Test void googleRequiresVerifiedEmailFromValidatedIdToken() throws Exception {
        var idToken = new OidcIdToken("test-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "google-sub", "email", "x@example.com", "email_verified", false));
        var auth = new OAuth2AuthenticationToken(new DefaultOidcUser(authorities, idToken), authorities, "google");
        var request = request(); var session = (MockHttpSession) request.getSession();
        var response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, auth);
        assertEquals("http://localhost:5173/oauth/callback#error=social_email_unverified", response.getRedirectedUrl());
        assertTrue(session.isInvalid());
        verifyNoInteractions(handoff);
    }

    @Test void successfulGoogleLoginReturnsOnlyOneTimeCodeAndDestroysSession() throws Exception {
        var idToken = new OidcIdToken("test-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "google-sub", "email", "x@example.com", "email_verified", true, "name", "Alice"));
        var auth = new OAuth2AuthenticationToken(new DefaultOidcUser(authorities, idToken), authorities, "google");
        when(handoff.create(new OAuthHandoffService.Identity("google", "google-sub", "x@example.com", "Alice", null), "A".repeat(43)))
                .thenReturn("one-time-code");
        var request = request(); var session = (MockHttpSession) request.getSession();
        var response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, auth);
        assertEquals("http://localhost:5173/oauth/callback#code=one-time-code", response.getRedirectedUrl());
        assertTrue(session.isInvalid());
    }

    @Test void githubUsesNumericSubjectAndVerifiedEmailRatherThanPublicEmail() throws Exception {
        var principal = new DefaultOAuth2User(authorities, Map.of("id", 123, "email", "untrusted@example.com",
                "codepath_verified_email", "verified@example.com", "name", "Dev"), "id");
        when(handoff.create(any(), anyString())).thenReturn("code");
        var request = request(); request.setAttribute(SocialAuthorizationRequests.LINK_USER, 1L);
        handler.onAuthenticationSuccess(request, new MockHttpServletResponse(), new OAuth2AuthenticationToken(principal, authorities, "github"));
        verify(handoff).create(new OAuthHandoffService.Identity("github", "123", "verified@example.com", "Dev", 1L), "A".repeat(43));
    }

    @Test void githubRequiresVerifiedPrimaryEmail() {
        assertEquals("private@example.com", GithubOAuthUserService.verifiedEmail(List.of(
                new GithubOAuthUserService.Email("private@example.com", true, true, "private"))));
        assertThrows(OAuth2AuthenticationException.class, () -> GithubOAuthUserService.verifiedEmail(List.of(
                new GithubOAuthUserService.Email("unverified@example.com", true, false, "public"))));
    }

    @Test void untrustedErrorTextNeverBecomesRedirectTargetOrQueryString() throws Exception {
        var response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(request(), response, new OAuth2AuthenticationException(new OAuth2Error("https://attacker.example/?token=secret")));
        assertEquals("http://localhost:5173/oauth/callback#error=social_failed", response.getRedirectedUrl());
    }
}
