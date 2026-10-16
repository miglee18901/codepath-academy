package com.codepath.academy.security;

import com.codepath.academy.service.OAuthHandoffService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialAuthorizationRequestsTest {
    private final SocialClientRegistrations clients = new SocialClientRegistrations(new MockEnvironment()
            .withProperty("app.frontend-url", "http://localhost:5173")
            .withProperty("app.oauth.google.client-id", "test-id").withProperty("app.oauth.google.client-secret", "test-secret"));
    private final OAuthHandoffService handoff = mock(OAuthHandoffService.class);
    private final SocialAuthorizationRequests repository = new SocialAuthorizationRequests(clients, handoff);

    @Test void stateIsBrowserBoundAndConsumedOnceWithPkceAndNonce() {
        var start = new MockHttpServletRequest("GET", SocialAuthorizationRequests.BASE + "/google");
        start.setServletPath(SocialAuthorizationRequests.BASE + "/google");
        start.addParameter("challenge", OAuthHandoffService.random());
        var options = repository.resolve(start);
        assertNotNull(options);
        assertEquals("S256", options.getAdditionalParameters().get("code_challenge_method"));
        assertNotNull(options.getAdditionalParameters().get("nonce"));
        assertFalse(options.getAdditionalParameters().containsKey(SocialAuthorizationRequests.CHALLENGE));
        repository.saveAuthorizationRequest(options, start, new MockHttpServletResponse());
        var wrongBrowser = new MockHttpServletRequest(); wrongBrowser.addParameter("state", options.getState());
        assertNull(repository.removeAuthorizationRequest(wrongBrowser, new MockHttpServletResponse()));
        var callback = new MockHttpServletRequest(); callback.setSession(start.getSession(false));
        callback.addParameter("state", "incorrect");
        assertNull(repository.removeAuthorizationRequest(callback, new MockHttpServletResponse()));
        callback.setParameter("state", options.getState());
        assertNotNull(repository.removeAuthorizationRequest(callback, new MockHttpServletResponse()));
        assertEquals(start.getParameter("challenge"), callback.getAttribute(SocialAuthorizationRequests.CHALLENGE));
        assertNull(repository.removeAuthorizationRequest(callback, new MockHttpServletResponse()));
    }

    @Test void requestWithMissingChallengeOrUnconfiguredProviderCannotStart() {
        var request = new MockHttpServletRequest();
        assertNull(repository.resolve(request, "google"));
        request.addParameter("challenge", OAuthHandoffService.random());
        assertNull(repository.resolve(request, "github"));
    }

    @Test void providerConfigurationDisablesMissingCredentialsAndRejectsPartialCredentials() {
        assertFalse(new SocialClientRegistrations(new MockEnvironment()).iterator().hasNext());
        assertThrows(IllegalArgumentException.class, () -> new SocialClientRegistrations(new MockEnvironment()
                .withProperty("app.oauth.google.client-id", "only-id")));
        assertEquals("http://localhost:5173/api/auth/oauth2/callback/google", clients.findByRegistrationId("google").getRedirectUri());
    }

    @Test void expiredStateCannotBeConsumed() {
        var start = new MockHttpServletRequest(); start.addParameter("challenge", OAuthHandoffService.random());
        var options = repository.resolve(start, "google");
        repository.saveAuthorizationRequest(options, start, new MockHttpServletResponse());
        start.getSession().setAttribute("codepath_oauth_issued_at", System.currentTimeMillis() - 301000);
        start.setParameter("state", options.getState());
        assertNull(repository.removeAuthorizationRequest(start, new MockHttpServletResponse()));
    }

    @Test void linkOwnerComesOnlyFromServerTicketAndIsNotSentToProvider() {
        when(handoff.consumeLinkTicket("T".repeat(43), "google")).thenReturn(42L);
        var request = new MockHttpServletRequest();
        request.addParameter("challenge", "A".repeat(43));
        request.addParameter("link", "T".repeat(43));
        request.addParameter("userId", "1");
        var options = repository.resolve(request, "google");
        assertEquals(42L, (Long) options.getAttribute(SocialAuthorizationRequests.LINK_USER));
        assertFalse(options.getAdditionalParameters().containsKey(SocialAuthorizationRequests.LINK_USER));
        assertFalse(options.getAuthorizationRequestUri().contains("T".repeat(43)));
    }

    @Test void insecureRemoteCallbackRejectedOnlyWhenOAuthEnabled() {
        var env = new MockEnvironment().withProperty("app.frontend-url", "http://192.168.1.2:5173");
        assertFalse(new SocialClientRegistrations(env).iterator().hasNext());
        env.withProperty("app.oauth.google.client-id", "test-id").withProperty("app.oauth.google.client-secret", "test-secret");
        assertThrows(IllegalArgumentException.class, () -> new SocialClientRegistrations(env));
    }
}
