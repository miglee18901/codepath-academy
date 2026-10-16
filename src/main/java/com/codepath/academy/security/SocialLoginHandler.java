package com.codepath.academy.security;

import com.codepath.academy.service.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class SocialLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private final OAuthHandoffService handoff;
    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        try {
            var auth = (OAuth2AuthenticationToken) authentication;
            var principal = auth.getPrincipal();
            String provider = auth.getAuthorizedClientRegistrationId();
            String subject, email;
            if ("google".equals(provider) && principal instanceof OidcUser oidc) {
                if (!Boolean.TRUE.equals(oidc.getIdToken().getEmailVerified()))
                    throw SocialAccountService.rejected("social_email_unverified");
                subject = oidc.getSubject(); email = oidc.getIdToken().getEmail();
            } else if ("github".equals(provider)) {
                subject = Objects.toString(principal.getAttribute("id"), null);
                email = principal.getAttribute("codepath_verified_email");
            } else throw SocialAccountService.rejected("social_failed");
            String challenge = (String) request.getAttribute(SocialAuthorizationRequests.CHALLENGE);
            if (challenge == null || !challenge.matches("[A-Za-z0-9_-]{43}")) throw SocialAccountService.rejected("social_failed");
            Long linkUser = (Long) request.getAttribute(SocialAuthorizationRequests.LINK_USER);
            String code = handoff.create(new OAuthHandoffService.Identity(provider, subject, email,
                    principal.getAttribute("name"), linkUser), challenge);
            clear(request);
            response.sendRedirect(frontend() + "/oauth/callback#code=" + code);
        } catch (OAuth2AuthenticationException e) { failure(request, response, e.getError().getErrorCode()); }
        catch (RuntimeException e) { failure(request, response, "social_failed"); }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        failure(request, response, exception instanceof OAuth2AuthenticationException e ? e.getError().getErrorCode() : "social_failed");
    }

    private void failure(HttpServletRequest request, HttpServletResponse response, String error) throws IOException {
        String safe = Set.of("access_denied", "social_email_unverified", "social_email_exists", "social_already_linked",
                "social_conflict").contains(error) ? error : "social_failed";
        clear(request);
        response.sendRedirect(frontend() + "/oauth/callback#error=" + safe);
    }

    private String frontend() { return frontendUrl.replaceAll("/+$", ""); }
    private static void clear(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }
}
