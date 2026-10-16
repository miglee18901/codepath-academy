package com.codepath.academy.service;

import com.codepath.academy.entity.User;
import com.codepath.academy.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialProfileServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final SocialIdentityRepository identities = mock(SocialIdentityRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final OAuthHandoffService handoff = mock(OAuthHandoffService.class);
    private final SocialProfileService service = new SocialProfileService(users, identities, passwords, handoff);
    private User user(boolean hasPassword) {
        User u = User.builder().id(1L).username("alice").password("hash").passwordLoginEnabled(hasPassword).build();
        when(users.findByUsername("alice")).thenReturn(Optional.of(u));
        when(users.lockById(1L)).thenReturn(Optional.of(u));
        return u;
    }

    @Test void linkingRequiresPasswordOrFreshOAuthProof() {
        user(true);
        assertThrows(ResponseStatusException.class, () -> service.startLink("alice", "google", "wrong", "Bearer old"));
        verify(handoff, never()).linkTicket(anyLong(), anyString());
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(handoff.linkTicket(1L, "google")).thenReturn("ticket");
        assertEquals("ticket", service.startLink("alice", "google", "correct", "Bearer old"));
    }

    @Test void socialOnlyAccountCanSetInitialPasswordWithFreshOAuthProof() {
        User u = user(false);
        when(handoff.isRecent("alice", "Bearer fresh")).thenReturn(true);
        when(passwords.encode("newpassword")).thenReturn("new-hash");
        service.setInitialPassword("alice", "newpassword", "Bearer fresh");
        assertTrue(u.isPasswordLoginEnabled()); assertEquals("new-hash", u.getPassword());
        verify(users).saveAndFlush(u);
    }

    @Test void oldJwtCannotSetInitialPassword() {
        user(false);
        assertThrows(ResponseStatusException.class, () -> service.setInitialPassword("alice", "newpassword", "Bearer old"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test void initialPasswordEndpointCannotResetExistingPassword() {
        user(true);
        assertThrows(ResponseStatusException.class, () -> service.setInitialPassword("alice", "newpassword", "Bearer fresh"));
        verify(users, never()).saveAndFlush(any());
    }
}
