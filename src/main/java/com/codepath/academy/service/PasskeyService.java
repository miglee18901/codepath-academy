package com.codepath.academy.service;

import com.codepath.academy.dto.AuthResponseDTO;
import com.codepath.academy.entity.*;
import com.codepath.academy.repository.*;
import com.codepath.academy.security.PasskeyCredentialStore;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PasskeyService {
    private final RelyingParty relyingParty;
    private final PasskeyCredentialRepository credentials;
    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwords;
    private final LoginTokenService tokens;
    private final ObjectMapper json;
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

    public record Options(String requestId, Object options) {}
    public record Summary(Long id, String label, LocalDateTime createdAt, LocalDateTime lastUsedAt) {}

    @Transactional
    public Options startRegistration(String username, String password, String label) {
        User current = user(username);
        // Serialize handle creation and the per-account credential limit.
        User user = users.lockById(current.getId()).orElseThrow();
        verifyPassword(user, password);
        if (credentials.countByUserId(user.getId()) >= 10) {
            throw new IllegalArgumentException("Tối đa 10 Passkey. Hãy xóa Passkey không dùng trước.");
        }
        if (user.getPasskeyHandle() == null) {
            byte[] handle = new byte[32];
            new SecureRandom().nextBytes(handle);
            user.setPasskeyHandle(new ByteArray(handle).getBase64Url());
            users.saveAndFlush(user);
        }
        var request = relyingParty.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder().name(user.getUsername()).displayName(user.getFullName())
                        .id(PasskeyCredentialStore.bytes(user.getPasskeyHandle())).build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED).build())
                .timeout(300000).build());
        try {
            String payload = user.getId() + "\n" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(label.trim().getBytes(StandardCharsets.UTF_8)) + "\n" + request.toJson();
            return save("register", payload, request.toCredentialsCreateJson());
        } catch (java.io.IOException e) { throw new IllegalStateException("Không tạo được yêu cầu Passkey", e); }
    }

    @Transactional
    public void finishRegistration(String username, String requestId, String response) {
        User current = user(username);
        String[] state = consume("register", requestId).split("\n", 3);
        if (state.length != 3 || !state[0].equals(current.getId().toString())) {
            throw invalid();
        }
        User owner = users.lockById(current.getId()).orElseThrow();
        if (credentials.countByUserId(owner.getId()) >= 10) throw new IllegalArgumentException("Tối đa 10 Passkey.");
        RegistrationResult result;
        try {
            var request = PublicKeyCredentialCreationOptions.fromJson(state[2]);
            if (!request.getUser().getId().getBase64Url().equals(owner.getPasskeyHandle())) throw invalid();
            result = relyingParty.finishRegistration(FinishRegistrationOptions.builder().request(request)
                    .response(PublicKeyCredential.parseRegistrationResponseJson(response)).build());
            if (!result.isUserVerified()) throw invalid();
        } catch (Exception e) { throw invalid(); }
        PasskeyCredential credential = new PasskeyCredential();
        credential.setUserId(owner.getId());
        credential.setCredentialId(result.getKeyId().getId().getBase64Url());
        credential.setPublicKeyCose(result.getPublicKeyCose().getBase64Url());
        credential.setSignatureCount(result.getSignatureCount());
        credential.setLabel(new String(Base64.getUrlDecoder().decode(state[1]), StandardCharsets.UTF_8));
        credential.setTransports(result.getKeyId().getTransports().orElseGet(TreeSet::new).stream()
                .map(AuthenticatorTransport::getId).collect(Collectors.joining(",")));
        credential.setBackupEligible(result.isBackupEligible());
        credential.setBackedUp(result.isBackedUp());
        credential.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        credentials.saveAndFlush(credential);
    }

    public Options startAuthentication() {
        var request = relyingParty.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED).timeout(300000).build());
        try { return save("login", request.toJson(), request.toCredentialsGetJson()); }
        catch (java.io.IOException e) { throw new IllegalStateException("Không tạo được yêu cầu Passkey", e); }
    }

    @Transactional
    public AuthResponseDTO finishAuthentication(String requestId, String response) {
        String state = consume("login", requestId);
        AssertionResult result;
        try {
            result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(AssertionRequest.fromJson(state))
                    .response(PublicKeyCredential.parseAssertionResponseJson(response)).build());
            if (!result.isSuccess() || !result.isUserVerified()) throw invalid();
        } catch (Exception e) { throw invalid(); }
        PasskeyCredential credential = credentials.findByCredentialId(result.getCredentialId().getBase64Url())
                .orElseThrow(PasskeyService::invalid);
        User owner = users.findById(credential.getUserId()).orElseThrow(PasskeyService::invalid);
        if (!owner.getUsername().equals(result.getUsername())) throw invalid();
        credential.setSignatureCount(result.getSignatureCount());
        credential.setBackedUp(result.isBackedUp());
        credential.setLastUsedAt(LocalDateTime.now(ZoneOffset.UTC));
        // Flush before issuing tokens; optimistic locking rejects concurrent stale counter updates/deletion.
        credentials.saveAndFlush(credential);
        return tokens.issue(owner.getUsername());
    }

    @Transactional(readOnly = true)
    public List<Summary> list(String username) {
        return credentials.findByUserIdOrderByCreatedAtDesc(user(username).getId()).stream()
                .map(c -> new Summary(c.getId(), c.getLabel(), c.getCreatedAt(), c.getLastUsedAt())).toList();
    }

    @Transactional
    public void delete(String username, Long id, String password) {
        User user = user(username);
        verifyPassword(user, password);
        var credential = credentials.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Passkey"));
        credentials.delete(credential);
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(PasskeyService::invalid);
    }

    private void verifyPassword(User user, String password) {
        if (!passwords.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không đúng");
        }
    }

    private Options save(String purpose, String state, String browserOptions) {
        String id = UUID.randomUUID().toString();
        redis.opsForValue().set("passkey:" + purpose + ":" + id, state, CHALLENGE_TTL);
        return new Options(id, json.readTree(browserOptions));
    }

    private String consume(String purpose, String id) {
        String state = redis.opsForValue().getAndDelete("passkey:" + purpose + ":" + id);
        if (state == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Yêu cầu Passkey đã hết hạn hoặc đã sử dụng. Vui lòng thử lại.");
        return state;
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không xác thực được Passkey. Vui lòng thử lại.");
    }
}
