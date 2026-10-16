package com.codepath.academy.service;

import com.codepath.academy.dto.AuthResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OAuthHandoffService {
    private final StringRedisTemplate redis;
    private final LoginTokenService tokens;
    private final SocialAccountService accounts;
    private final ObjectMapper json;

    public record Identity(String provider, String subject, String email, String name, Long linkUserId) {}
    public record Pending(Identity identity, String challenge) {}

    public static String digest(String value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static String random() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String create(Identity identity, String challenge) {
        if (challenge == null || !challenge.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String code = random();
        redis.opsForValue().set("oauth:handoff:" + digest(code), json.writeValueAsString(new Pending(identity, challenge)), Duration.ofSeconds(60));
        return code;
    }

    public AuthResponseDTO exchange(String code, String verifier) {
        String state = redis.opsForValue().getAndDelete("oauth:handoff:" + digest(code));
        if (state == null) throw invalid();
        Pending pending = json.readValue(state, Pending.class);
        if (!MessageDigest.isEqual(pending.challenge().getBytes(StandardCharsets.US_ASCII),
                digest(verifier).getBytes(StandardCharsets.US_ASCII))) throw invalid();
        Identity identity = pending.identity();
        String username;
        try {
            // No account creation/linking before the initiating browser proves possession of its verifier.
            username = accounts.resolve(identity.provider(), identity.subject(), identity.email(), identity.name(), identity.linkUserId()).getUsername();
        } catch (OAuth2AuthenticationException e) {
            String message = switch (e.getError().getErrorCode()) {
                case "social_email_exists" -> "Email này đã thuộc tài khoản hiện có. Hãy đăng nhập tài khoản đó rồi liên kết Google/GitHub trong Hồ sơ cá nhân.";
                case "social_already_linked" -> "Tài khoản Google/GitHub này đã được liên kết. Hãy dùng đúng tài khoản hoặc nhà cung cấp khác.";
                default -> "Cần email chính đã được xác minh để đăng nhập Google/GitHub.";
            };
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thông tin tài khoản vừa thay đổi. Vui lòng đăng nhập lại hoặc liên kết từ Hồ sơ cá nhân.");
        }
        AuthResponseDTO auth = tokens.issue(username);
        redis.opsForValue().set("oauth:recent:" + digest(auth.getToken()), username, Duration.ofMinutes(5));
        return auth;
    }

    public boolean isRecent(String username, String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return false;
        return username.equals(redis.opsForValue().get("oauth:recent:" + digest(authorization.substring(7))));
    }

    public String linkTicket(Long userId, String provider) {
        String ticket = random();
        redis.opsForValue().set("oauth:link:" + digest(ticket), userId + "\n" + provider, Duration.ofMinutes(5));
        return ticket;
    }

    public Long consumeLinkTicket(String ticket, String provider) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String state = redis.opsForValue().getAndDelete("oauth:link:" + digest(ticket));
        if (state == null) throw invalid();
        String[] parts = state.split("\n", 2);
        if (parts.length != 2 || !parts[1].equals(provider)) throw invalid();
        return Long.valueOf(parts[0]);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại.");
    }
}
