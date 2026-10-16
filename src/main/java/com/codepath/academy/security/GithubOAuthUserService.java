package com.codepath.academy.security;

import com.codepath.academy.service.SocialAccountService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.*;

@Component
public class GithubOAuthUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient client;
    public GithubOAuthUserService() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().requestFactory(factory).build();
    }

    public record Email(String email, boolean primary, boolean verified, String visibility) {}

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        if (!"github".equals(request.getClientRegistration().getRegistrationId())) throw SocialAccountService.rejected("social_failed");
        OAuth2User user = delegate.loadUser(request);
        List<Email> emails;
        try {
            emails = client.get().uri("https://api.github.com/user/emails?per_page=100")
                    .headers(h -> { h.setBearerAuth(request.getAccessToken().getTokenValue());
                        h.set("Accept", "application/vnd.github+json"); h.set("X-GitHub-Api-Version", "2022-11-28"); })
                    .retrieve().body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) { throw SocialAccountService.rejected("social_email_unverified"); }
        var attributes = new HashMap<>(user.getAttributes());
        attributes.put("codepath_verified_email", verifiedEmail(emails));
        return new DefaultOAuth2User(user.getAuthorities(), attributes, "id");
    }

    public static String verifiedEmail(List<Email> emails) {
        if (emails == null) throw SocialAccountService.rejected("social_email_unverified");
        return emails.stream().filter(e -> e.primary() && e.verified() && e.email() != null)
                .map(Email::email).findFirst().orElseThrow(() -> SocialAccountService.rejected("social_email_unverified"));
    }
}
