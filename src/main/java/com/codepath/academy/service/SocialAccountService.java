package com.codepath.academy.service;

import com.codepath.academy.entity.*;
import com.codepath.academy.enums.Role;
import com.codepath.academy.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SocialAccountService {
    private final UserRepository users;
    private final SocialIdentityRepository identities;
    private final PasswordEncoder passwords;

    @Transactional
    public User resolve(String provider, String subject, String email, String displayName, Long linkUserId) {
        if (!Set.of("google", "github").contains(provider) || subject == null
                || !subject.matches("[\\x21-\\x7e]{1,255}") || email == null || email.isBlank()
                || email.length() > 100 || !email.contains("@")) throw rejected("social_email_unverified");
        var existing = identities.findByProviderAndSubject(provider, subject);
        if (existing.isPresent()) {
            if (linkUserId != null && !linkUserId.equals(existing.get().getUserId())) throw rejected("social_already_linked");
            return users.findById(existing.get().getUserId()).orElseThrow();
        }
        User owner;
        if (linkUserId != null) {
            owner = users.lockById(linkUserId).orElseThrow();
            if (identities.existsByUserIdAndProvider(owner.getId(), provider)) throw rejected("social_already_linked");
        } else {
            // Email is contact information, never the key used to silently merge accounts.
            if (users.existsByEmail(email)) throw rejected("social_email_exists");
            String name = displayName == null || displayName.isBlank() ? "Học viên" : displayName.trim();
            name = name.substring(0, name.offsetByCodePoints(0, Math.min(50, name.codePointCount(0, name.length()))));
            owner = users.saveAndFlush(User.builder()
                    .username(provider.charAt(0) + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                    .email(email).fullName(name).role(Role.STUDENT)
                    .password(passwords.encode(UUID.randomUUID() + UUID.randomUUID().toString()))
                    .passwordLoginEnabled(false).build());
        }
        SocialIdentity identity = new SocialIdentity();
        identity.setUserId(owner.getId());
        identity.setProvider(provider);
        identity.setSubject(subject);
        identity.setProviderEmail(email);
        identity.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        identities.saveAndFlush(identity);
        return owner;
    }

    public static OAuth2AuthenticationException rejected(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }
}
