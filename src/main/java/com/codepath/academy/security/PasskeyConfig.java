package com.codepath.academy.security;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.PublicKeyCredentialParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
public class PasskeyConfig {
    @Bean
    RelyingParty passkeyRelyingParty(PasskeyCredentialStore store,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.passkeys.rp-id:}") String rpId,
            @Value("${app.passkeys.origins:}") String origins) {
        String host = URI.create(frontendUrl).getHost();
        Set<String> allowed = origins.isBlank() ? Set.of(frontendUrl) : Arrays.stream(origins.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(rpId.isBlank() ? host : rpId)
                        .name("CodePath Academy").build())
                .credentialRepository(store).origins(allowed)
                // Java 17 supports these algorithms without installing an extra JCA provider.
                .preferredPubkeyParams(List.of(PublicKeyCredentialParameters.ES256, PublicKeyCredentialParameters.RS256))
                .allowOriginPort(false).allowOriginSubdomain(false).build();
    }
}
