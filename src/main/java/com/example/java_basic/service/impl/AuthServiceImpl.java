package com.example.java_basic.service.impl;

import com.example.java_basic.dto.*;
import com.example.java_basic.entity.User;
import com.example.java_basic.enums.Role;
import com.example.java_basic.repository.UserRepository;
import com.example.java_basic.security.JwtService;
import com.example.java_basic.service.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final EmailService emailService;
    private final CacheManager cacheManager;
    private final StringRedisTemplate redisTemplate;
    private final CaptchaService captchaService;
    private final SecureRandom random = new SecureRandom();

    @Value("${jwt.refresh.expiration:604800000}")
    private long refreshExpiration;

    private Cache cache() {
        Cache c = cacheManager.getCache("otpCache");
        if (c == null) throw new IllegalStateException("OTP chưa sẵn sàng");
        return c;
    }

    private String code() {
        return String.format("%06d", random.nextInt(1000000));
    }

    @Override
    public synchronized void initiateRegistration(RegisterRequestDTO request) {
        if (
                !captchaService.verifyToken(request.getRecaptchaToken())
        ) throw new IllegalArgumentException("Xác thực CAPTCHA thất bại");
        if (
                userRepository.existsByUsername(request.getUsername()) ||
                        userRepository.existsByEmail(request.getEmail())
        ) throw new IllegalArgumentException("Tên đăng nhập hoặc email đã tồn tại");
        OtpSessionData old = cache().get(request.getEmail(), OtpSessionData.class);
        if (
                old != null &&
                        old.getSentAt().plusSeconds(60).isAfter(LocalDateTime.now())
        ) throw new IllegalStateException("Vui lòng đợi 60 giây trước khi gửi lại");
        // Never cache the plaintext password.
        request.setPassword(passwordEncoder.encode(request.getPassword()));
        request.setRecaptchaToken(null);
        OtpSessionData s = OtpSessionData.builder()
                .userData(request)
                .otpCode(code())
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .sentAt(LocalDateTime.now())
                .attempts(0)
                .build();
        cache().put(request.getEmail(), s);
        emailService.sendOtpEmail(
                request.getEmail(),
                request.getFullName(),
                s.getOtpCode()
        );
    }

    @Override
    public synchronized void resendOTP(String email) {
        OtpSessionData s = cache().get(email, OtpSessionData.class);
        if (s == null) throw new IllegalArgumentException("Phiên đăng ký hết hạn");
        if (
                s.getSentAt().plusSeconds(60).isAfter(LocalDateTime.now())
        ) throw new IllegalStateException("Vui lòng đợi 60 giây trước khi gửi lại");
        s.setOtpCode(code());
        s.setSentAt(LocalDateTime.now());
        s.setExpiryTime(LocalDateTime.now().plusMinutes(5));
        s.setAttempts(0);
        cache().put(email, s);
        emailService.sendOtpEmail(
                email,
                s.getUserData().getFullName(),
                s.getOtpCode()
        );
    }

    @Override
    @Transactional
    public synchronized void verifyRegistration(OtpVerifyRequestDTO request) {
        OtpSessionData s = cache().get(request.getEmail(), OtpSessionData.class);
        if (
                s == null || s.getExpiryTime().isBefore(LocalDateTime.now())
        ) throw new IllegalArgumentException("Mã OTP đã hết hạn");
        if (!s.getOtpCode().equals(request.getOtp())) {
            s.setAttempts(s.getAttempts() + 1);
            if (s.getAttempts() >= 5) cache().evict(request.getEmail());
            else cache().put(request.getEmail(), s);
            throw new IllegalArgumentException("OTP không hợp lệ. Tối đa 5 lần thử.");
        }
        RegisterRequestDTO r = s.getUserData();
        if (
                userRepository.existsByUsername(r.getUsername()) ||
                        userRepository.existsByEmail(r.getEmail())
        ) throw new IllegalArgumentException("Tên đăng nhập hoặc email đã tồn tại");
        User u = User.builder()
                .username(r.getUsername())
                .password(r.getPassword())
                .fullName(r.getFullName())
                .email(r.getEmail())
                .role(Role.STUDENT)
                .build();
        userRepository.save(u);
        cache().evict(request.getEmail());
    }

    @Override
    public AuthResponseDTO login(LoginRequestDTO request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );
        return tokens(request.getUsername());
    }

    @Override
    public AuthResponseDTO refreshToken(RefreshTokenRequestDTO request) {
        String username = redisTemplate
                .opsForValue()
                .getAndDelete("refresh:" + request.getRefreshToken());
        if (username == null) throw new BadCredentialsException(
                "Refresh token hết hạn"
        );
        return tokens(username);
    }

    private AuthResponseDTO tokens(String username) {
        UserDetails details = userDetailsService.loadUserByUsername(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        String refresh = UUID.randomUUID().toString();
        redisTemplate
                .opsForValue()
                .set(
                        "refresh:" + refresh,
                        username,
                        refreshExpiration,
                        TimeUnit.MILLISECONDS
                );
        return AuthResponseDTO.builder()
                .token(jwtService.generateToken(details))
                .refreshToken(refresh)
                .id(user.getId())
                .username(username)
                .role("ROLE_" + user.getRole().name())
                .build();
    }
}
