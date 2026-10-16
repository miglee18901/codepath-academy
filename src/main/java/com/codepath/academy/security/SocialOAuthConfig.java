package com.codepath.academy.security;

import com.codepath.academy.service.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class SocialOAuthConfig {
    @Bean @Order(1)
    SecurityFilterChain socialOAuthChain(HttpSecurity http, SocialClientRegistrations clients,
            OAuthHandoffService handoff, GithubOAuthUserService github, SocialLoginHandler handler) throws Exception {
        var requests = new SocialAuthorizationRequests(clients, handoff);
        return http.securityMatcher("/api/auth/oauth2/authorization/**", "/api/auth/oauth2/callback/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(c -> c.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .oauth2Login(o -> o.clientRegistrationRepository(clients)
                        .authorizedClientRepository(new DiscardProviderTokens())
                        .loginPage("/login")
                        .authorizationEndpoint(a -> a.baseUri(SocialAuthorizationRequests.BASE)
                                .authorizationRequestResolver(requests).authorizationRequestRepository(requests))
                        .redirectionEndpoint(r -> r.baseUri("/api/auth/oauth2/callback/*"))
                        .userInfoEndpoint(u -> u.userService(github))
                        .successHandler(handler).failureHandler(handler))
                .build();
    }

    // Provider tokens are needed only while resolving identity, never stored as application sessions.
    static class DiscardProviderTokens implements OAuth2AuthorizedClientRepository {
        @Override public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, Authentication auth, HttpServletRequest request) { return null; }
        @Override public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication auth, HttpServletRequest request, HttpServletResponse response) {}
        @Override public void removeAuthorizedClient(String id, Authentication auth, HttpServletRequest request, HttpServletResponse response) {}
    }
}
