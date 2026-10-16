package com.codepath.academy.service;

import com.codepath.academy.dto.AuthResponseDTO;
import com.codepath.academy.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthHandoffServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final LoginTokenService tokens = mock(LoginTokenService.class);
    private final SocialAccountService accounts = mock(SocialAccountService.class);
    private final OAuthHandoffService service = new OAuthHandoffService(redis, tokens, accounts, JsonMapper.builder().build());
    private final OAuthHandoffService.Identity identity = new OAuthHandoffService.Identity("google", "sub", "a@example.com", "Alice", null);
    private final Map<String, String> store = new HashMap<>();
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(c -> { store.put(c.getArgument(0), c.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(c -> store.get(c.getArgument(0)));
        when(values.getAndDelete(anyString())).thenAnswer(c -> store.remove(c.getArgument(0)));
    }

    @Test void exchangeRequiresBrowserVerifierAndCodeIsOneUse() {
        String verifier = OAuthHandoffService.random();
        String code = service.create(identity, OAuthHandoffService.digest(verifier));
        when(accounts.resolve("google", "sub", "a@example.com", "Alice", null)).thenReturn(User.builder().username("alice").build());
        var auth = AuthResponseDTO.builder().token("jwt").build();
        when(tokens.issue("alice")).thenReturn(auth);
        assertSame(auth, service.exchange(code, verifier));
        assertTrue(service.isRecent("alice", "Bearer jwt"));
        assertFalse(service.isRecent("bob", "Bearer jwt"));
        assertThrows(ResponseStatusException.class, () -> service.exchange(code, verifier));
        verify(tokens, times(1)).issue("alice");
        verify(redis.opsForValue()).set(eq("oauth:handoff:" + OAuthHandoffService.digest(code)), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test void stolenCodeWithoutVerifierCannotLogIn() {
        String code = service.create(identity, OAuthHandoffService.digest(OAuthHandoffService.random()));
        assertThrows(ResponseStatusException.class, () -> service.exchange(code, OAuthHandoffService.random()));
        verifyNoInteractions(tokens, accounts);
    }

    @Test void expiredOrFabricatedCodeDoesNotIssueTokens() {
        assertThrows(ResponseStatusException.class, () -> service.exchange(OAuthHandoffService.random(), OAuthHandoffService.random()));
        verifyNoInteractions(tokens, accounts);
    }

    @Test void linkTicketIsBoundToProviderAndOneUse() {
        String ticket = service.linkTicket(1L, "google");
        assertEquals(1L, service.consumeLinkTicket(ticket, "google"));
        assertThrows(ResponseStatusException.class, () -> service.consumeLinkTicket(ticket, "google"));
        String another = service.linkTicket(1L, "github");
        assertThrows(ResponseStatusException.class, () -> service.consumeLinkTicket(another, "google"));
    }

    @Test void invalidBrowserChallengeIsRejectedBeforeCreatingCode() {
        assertThrows(ResponseStatusException.class, () -> service.create(identity, "bad"));
        assertTrue(store.isEmpty());
    }

    @Test void sharedLinkUrlCannotCreateLinkWithoutOriginalBrowserProof() {
        var linkIdentity = new OAuthHandoffService.Identity("github", "123", "victim@example.com", "Victim", 42L);
        String code = service.create(linkIdentity, OAuthHandoffService.digest(OAuthHandoffService.random()));
        verifyNoInteractions(accounts);
        assertThrows(ResponseStatusException.class, () -> service.exchange(code, OAuthHandoffService.random()));
        verifyNoInteractions(accounts, tokens);
    }

    @Test void verifiedBrowserCanCompleteExplicitLinkOnlyAfterProof() {
        String verifier = OAuthHandoffService.random();
        var linkIdentity = new OAuthHandoffService.Identity("github", "123", "alice@example.com", "Alice", 42L);
        String code = service.create(linkIdentity, OAuthHandoffService.digest(verifier));
        verifyNoInteractions(accounts);
        when(accounts.resolve("github", "123", "alice@example.com", "Alice", 42L))
                .thenReturn(User.builder().username("alice").build());
        when(tokens.issue("alice")).thenReturn(AuthResponseDTO.builder().token("jwt").build());
        service.exchange(code, verifier);
        var order = inOrder(accounts, tokens);
        order.verify(accounts).resolve("github", "123", "alice@example.com", "Alice", 42L);
        order.verify(tokens).issue("alice");
    }
}
