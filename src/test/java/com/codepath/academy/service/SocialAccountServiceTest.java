package com.codepath.academy.service;

import com.codepath.academy.entity.*;
import com.codepath.academy.enums.Role;
import com.codepath.academy.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialAccountServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final SocialIdentityRepository identities = mock(SocialIdentityRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final SocialAccountService service = new SocialAccountService(users, identities, passwords);

    @Test void newAccountAlwaysStudentWithNoUsableInitialPassword() {
        when(passwords.encode(anyString())).thenReturn("random-hash");
        when(users.saveAndFlush(any())).thenAnswer(call -> { User u = call.getArgument(0); u.setId(42L); return u; });
        User user = service.resolve("google", "google-sub", "new@example.com", "New Student", null);
        assertEquals(Role.STUDENT, user.getRole());
        assertFalse(user.isPasswordLoginEnabled());
        assertEquals("random-hash", user.getPassword());
        assertTrue(user.getUsername().length() <= 20);
        var identity = ArgumentCaptor.forClass(SocialIdentity.class);
        verify(identities).saveAndFlush(identity.capture());
        assertEquals(42L, identity.getValue().getUserId());
        assertEquals("google-sub", identity.getValue().getSubject());
    }

    @Test void matchingEmailNeverSilentlyLinksExistingAccount() {
        when(users.existsByEmail("admin@example.com")).thenReturn(true);
        var error = assertThrows(OAuth2AuthenticationException.class,
                () -> service.resolve("google", "attacker", "admin@example.com", "Admin", null));
        assertEquals("social_email_exists", error.getError().getErrorCode());
        verify(users, never()).saveAndFlush(any());
        verify(identities, never()).saveAndFlush(any());
    }

    @Test void stableProviderSubjectRetainsOriginalOwnerWhenEmailChanges() {
        SocialIdentity identity = new SocialIdentity(); identity.setUserId(2L);
        User owner = User.builder().id(2L).username("original").role(Role.STUDENT).build();
        when(identities.findByProviderAndSubject("github", "123")).thenReturn(Optional.of(identity));
        when(users.findById(2L)).thenReturn(Optional.of(owner));
        assertSame(owner, service.resolve("github", "123", "changed@example.com", "Changed", null));
        verify(users, never()).existsByEmail(anyString());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void explicitLinkPreservesExistingRoleAndProfile() {
        User admin = User.builder().id(1L).username("admin").role(Role.ADMIN).email("admin@local.test").build();
        when(users.lockById(1L)).thenReturn(Optional.of(admin));
        assertSame(admin, service.resolve("github", "123", "other@example.com", "Other", 1L));
        assertEquals(Role.ADMIN, admin.getRole());
        assertEquals("admin@local.test", admin.getEmail());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void identityCannotBeReassignedToAnotherAccount() {
        SocialIdentity identity = new SocialIdentity(); identity.setUserId(2L);
        when(identities.findByProviderAndSubject("github", "123")).thenReturn(Optional.of(identity));
        assertThrows(OAuth2AuthenticationException.class,
                () -> service.resolve("github", "123", "other@example.com", "Other", 1L));
        verify(identities, never()).saveAndFlush(any());
    }

    @Test void onlyOneIdentityPerProviderCanBeLinked() {
        when(users.lockById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(identities.existsByUserIdAndProvider(1L, "google")).thenReturn(true);
        assertThrows(OAuth2AuthenticationException.class,
                () -> service.resolve("google", "another", "another@example.com", "Other", 1L));
        verify(identities, never()).saveAndFlush(any());
    }

    @Test void unsupportedProvidersAndMissingIdentifiersAreRejected() {
        assertThrows(OAuth2AuthenticationException.class, () -> service.resolve("unknown", "123", "x@y.test", "X", null));
        assertThrows(OAuth2AuthenticationException.class, () -> service.resolve("google", null, "x@y.test", "X", null));
        assertThrows(OAuth2AuthenticationException.class, () -> service.resolve("google", "123", null, "X", null));
        verifyNoInteractions(users, identities);
    }
}
