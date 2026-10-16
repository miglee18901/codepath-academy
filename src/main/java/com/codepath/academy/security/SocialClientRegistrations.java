package com.codepath.academy.security;

import org.springframework.core.env.Environment;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

@Component
public class SocialClientRegistrations implements ClientRegistrationRepository, Iterable<ClientRegistration> {
    private final Map<String, ClientRegistration> registrations = new LinkedHashMap<>();

    public SocialClientRegistrations(Environment env) {
        String base = env.getProperty("app.oauth.callback-base-url", "");
        if (base.isBlank()) base = env.getProperty("app.frontend-url", "http://localhost:5173");
        base = base.replaceAll("/+$", "");
        for (String id : List.of("google", "github")) {
            String clientId = env.getProperty("app.oauth." + id + ".client-id", "");
            String secret = env.getProperty("app.oauth." + id + ".client-secret", "");
            if (clientId.isBlank() && secret.isBlank()) continue;
            if (clientId.isBlank() || secret.isBlank()) throw new IllegalArgumentException("OAuth " + id + " cần cả Client ID và Client Secret");
            validateBase(base);
            var builder = (id.equals("google") ? CommonOAuth2Provider.GOOGLE : CommonOAuth2Provider.GITHUB).getBuilder(id)
                    .clientId(clientId).clientSecret(secret)
                    .redirectUri(base + "/api/auth/oauth2/callback/" + id);
            builder.scope(id.equals("google") ? new String[]{"openid", "profile", "email"} : new String[]{"read:user", "user:email"});
            registrations.put(id, builder.build());
        }
    }

    @Override public ClientRegistration findByRegistrationId(String id) { return registrations.get(id); }
    @Override public Iterator<ClientRegistration> iterator() { return registrations.values().iterator(); }

    private static void validateBase(String base) {
        URI uri = URI.create(base);
        if (uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null
                || !uri.getPath().isEmpty() || !("https".equals(uri.getScheme()) ||
                ("http".equals(uri.getScheme()) && Set.of("localhost", "127.0.0.1").contains(uri.getHost())))) {
            throw new IllegalArgumentException("OAuth callback base URL phải là HTTPS origin hoặc HTTP localhost");
        }
    }
}
