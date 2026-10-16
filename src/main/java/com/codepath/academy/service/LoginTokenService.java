package com.codepath.academy.service;

import com.codepath.academy.dto.AuthResponseDTO;
import com.codepath.academy.repository.UserRepository;
import com.codepath.academy.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LoginTokenService {
    private final UserRepository users;
    private final JwtService jwt;
    private final UserDetailsService userDetails;
    private final StringRedisTemplate redis;
    @Value("${jwt.refresh.expiration:604800000}")
    private long refreshExpiration;

    // Only call after an authentication mechanism has verified the account owner.
    public AuthResponseDTO issue(String username) {
        var details = userDetails.loadUserByUsername(username);
        var user = users.findByUsername(username).orElseThrow();
        String refresh = UUID.randomUUID().toString();
        redis.opsForValue().set("refresh:" + refresh, username, refreshExpiration, TimeUnit.MILLISECONDS);
        return AuthResponseDTO.builder().token(jwt.generateToken(details)).refreshToken(refresh)
                .id(user.getId()).username(username).role("ROLE_" + user.getRole().name()).build();
    }
}
