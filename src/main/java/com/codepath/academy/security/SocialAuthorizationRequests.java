package com.codepath.academy.security;

import com.codepath.academy.service.OAuthHandoffService;
import jakarta.servlet.http.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public class SocialAuthorizationRequests implements OAuth2AuthorizationRequestResolver,
        AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    public static final String BASE = "/api/auth/oauth2/authorization";
    public static final String CHALLENGE = "codepath_handoff_challenge";
    public static final String LINK_USER = "codepath_link_user";
    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final HttpSessionOAuth2AuthorizationRequestRepository sessions = new HttpSessionOAuth2AuthorizationRequestRepository();
    private final SocialClientRegistrations clients;
    private final OAuthHandoffService handoff;
    private static final String ISSUED_AT = "codepath_oauth_issued_at";

    public SocialAuthorizationRequests(SocialClientRegistrations clients, OAuthHandoffService handoff) {
        this.clients = clients; this.handoff = handoff;
        delegate = new DefaultOAuth2AuthorizationRequestResolver(clients, BASE);
        delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    }

    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(BASE + "/")) return null;
        return resolve(request, path.substring((BASE + "/").length()));
    }

    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        String challenge = request.getParameter("challenge");
        if (clients.findByRegistrationId(registrationId) == null || challenge == null
                || !challenge.matches("[A-Za-z0-9_-]{43}")) return null;
        Long linkUser;
        try {
            linkUser = request.getParameter("link") == null ? null
                    : handoff.consumeLinkTicket(request.getParameter("link"), registrationId);
        } catch (RuntimeException e) { return null; }
        var original = delegate.resolve(request, registrationId);
        if (original == null) return null;
        return OAuth2AuthorizationRequest.from(original).attributes(a -> {
            a.put(CHALLENGE, challenge);
            if (linkUser != null) a.put(LINK_USER, linkUser);
        }).build();
    }

    @Override public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(ISSUED_AT) instanceof Long issued)
                || System.currentTimeMillis() - issued > 300000) return null;
        return sessions.loadAuthorizationRequest(request);
    }

    @Override public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest != null) {
            request.getSession().setMaxInactiveInterval(300);
            request.getSession().setAttribute(ISSUED_AT, System.currentTimeMillis());
        }
        sessions.saveAuthorizationRequest(authorizationRequest, request, response);
    }

    @Override public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        if (loadAuthorizationRequest(request) == null) return null;
        var original = sessions.removeAuthorizationRequest(request, response);
        if (original != null) {
            request.setAttribute(CHALLENGE, original.getAttribute(CHALLENGE));
            request.setAttribute(LINK_USER, original.getAttribute(LINK_USER));
        }
        return original;
    }
}
