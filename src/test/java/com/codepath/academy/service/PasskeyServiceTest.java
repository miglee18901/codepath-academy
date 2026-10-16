package com.codepath.academy.service;

import com.codepath.academy.entity.*;
import com.codepath.academy.repository.*;
import com.codepath.academy.security.*;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasskeyServiceTest {
    private final PasskeyCredentialRepository credentials = mock(PasskeyCredentialRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final LoginTokenService tokens = mock(LoginTokenService.class);
    private final Map<String, String> challenges = new HashMap<>();
    private PasskeyService service;
    private User alice;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call -> {
            assertEquals(Duration.ofMinutes(5), call.getArgument(2));
            challenges.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.getAndDelete(anyString())).thenAnswer(call -> challenges.remove(call.getArgument(0)));
        alice = User.builder().id(1L).username("alice").fullName("Alice").password("hash").build();
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(users.lockById(1L)).thenReturn(Optional.of(alice));
        when(passwords.matches("correct", "hash")).thenReturn(true);
        var rp = RelyingParty.builder().identity(RelyingPartyIdentity.builder().id("localhost").name("Test").build())
                .credentialRepository(new PasskeyCredentialStore(credentials, users))
                .origins(Set.of("http://localhost:5173")).build();
        service = new PasskeyService(rp, credentials, users, redis, passwords, tokens, JsonMapper.builder().build());
    }

    @Test
    void registrationRequiresPasswordAndNeverWritesCredentialOnFailure() {
        assertThrows(ResponseStatusException.class, () -> service.startRegistration("alice", "wrong", "Laptop"));
        assertTrue(challenges.isEmpty());
        verify(credentials, never()).saveAndFlush(any());
    }

    @Test
    void registrationUsesStableOpaqueHandleAndRequiresDiscoverableVerifiedCredentials() throws Exception {
        var first = service.startRegistration("alice", "correct", "Laptop");
        String handle = alice.getPasskeyHandle();
        assertEquals(32, ByteArray.fromBase64Url(handle).size());
        var request = PublicKeyCredentialCreationOptions.fromJson(challenges.get("passkey:register:" + first.requestId()).split("\n", 3)[2]);
        assertEquals(UserVerificationRequirement.REQUIRED, request.getAuthenticatorSelection().orElseThrow().getUserVerification().orElseThrow());
        assertEquals(ResidentKeyRequirement.REQUIRED, request.getAuthenticatorSelection().orElseThrow().getResidentKey().orElseThrow());
        assertEquals(handle, request.getUser().getId().getBase64Url());
        service.startRegistration("alice", "correct", "Phone");
        assertEquals(handle, alice.getPasskeyHandle());
    }

    @Test
    void challengeIsConsumedEvenOnInvalidResponseAndCannotBeReplayed() {
        var options = service.startAuthentication();
        assertThrows(ResponseStatusException.class, () -> service.finishAuthentication(options.requestId(), "{}"));
        assertFalse(challenges.containsKey("passkey:login:" + options.requestId()));
        var replay = assertThrows(ResponseStatusException.class, () -> service.finishAuthentication(options.requestId(), "{}"));
        assertTrue(replay.getReason().contains("hết hạn"));
        verifyNoInteractions(tokens);
    }

    @Test
    void registrationChallengeCannotBeUsedByAnotherUserOrForLogin() {
        var options = service.startRegistration("alice", "correct", "Laptop");
        assertThrows(ResponseStatusException.class, () -> service.finishAuthentication(options.requestId(), "{}"));
        assertTrue(challenges.containsKey("passkey:register:" + options.requestId()));
        when(users.findByUsername("bob")).thenReturn(Optional.of(User.builder().id(2L).username("bob").build()));
        assertThrows(ResponseStatusException.class, () -> service.finishRegistration("bob", options.requestId(), "{}"));
        verify(credentials, never()).saveAndFlush(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void expiredChallengeNeverIssuesTokens() {
        assertThrows(ResponseStatusException.class, () -> service.finishAuthentication(UUID.randomUUID().toString(), "{}"));
        verifyNoInteractions(tokens);
    }

    @Test
    void credentialLimitCheckedBeforeCreatingChallenge() {
        when(credentials.countByUserId(1L)).thenReturn(10L);
        assertThrows(IllegalArgumentException.class, () -> service.startRegistration("alice", "correct", "Laptop"));
        assertTrue(challenges.isEmpty());
    }

    @Test
    void deleteRequiresPasswordAndCredentialOwnership() {
        assertThrows(ResponseStatusException.class, () -> service.delete("alice", 5L, "wrong"));
        verify(credentials, never()).findByIdAndUserId(anyLong(), anyLong());
        when(credentials.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.delete("alice", 5L, "correct"));
        verify(credentials, never()).delete(any());
    }
}
