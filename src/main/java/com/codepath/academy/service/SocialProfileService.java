package com.codepath.academy.service;

import com.codepath.academy.entity.User;
import com.codepath.academy.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SocialProfileService {
    private final UserRepository users;
    private final SocialIdentityRepository identities;
    private final PasswordEncoder passwords;
    private final OAuthHandoffService handoff;
    public record Link(String provider, String email, LocalDateTime createdAt) {}

    @Transactional(readOnly = true)
    public List<Link> list(String username) {
        return identities.findByUserId(user(username).getId()).stream()
                .map(i -> new Link(i.getProvider(), i.getProviderEmail(), i.getCreatedAt())).toList();
    }

    public String startLink(String username, String provider, String password, String authorization) {
        User user = user(username);
        boolean passwordVerified = user.isPasswordLoginEnabled() && password != null
                && passwords.matches(password, user.getPassword());
        if (!passwordVerified && !handoff.isRecent(username, authorization)) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Nhập đúng mật khẩu hiện tại hoặc đăng nhập lại bằng Google/GitHub trong 5 phút gần nhất.");
        return handoff.linkTicket(user.getId(), provider);
    }

    @Transactional
    public void setInitialPassword(String username, String newPassword, String authorization) {
        User owner = user(username);
        User user = users.lockById(owner.getId()).orElseThrow();
        if (user.isPasswordLoginEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài khoản đã có mật khẩu.");
        if (!handoff.isRecent(username, authorization)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Hãy đăng nhập lại bằng Google/GitHub rồi thiết lập mật khẩu trong vòng 5 phút.");
        user.setPassword(passwords.encode(newPassword));
        user.setPasswordLoginEnabled(true);
        users.saveAndFlush(user);
    }

    private User user(String username) { return users.findByUsername(username).orElseThrow(); }
}
