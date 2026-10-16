package com.codepath.academy.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codepath.academy.dto.*;
import com.codepath.academy.entity.User;
import com.codepath.academy.enums.Role;
import com.codepath.academy.repository.UserRepository;
import com.codepath.academy.service.impl.AuthServiceImpl;

import java.time.LocalDateTime;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    AuthenticationManager authenticationManager;

    @Mock
    EmailService emailService;

    @Mock
    CacheManager cacheManager;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    CaptchaService captchaService;

    @Mock
    LoginTokenService loginTokenService;

    @Mock
    Cache cache;

    @InjectMocks
    AuthServiceImpl service;

    @Test
    void passwordLoginStillAuthenticatesBeforeIssuingTokens() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("learner");
        request.setPassword("correct");
        AuthResponseDTO response = AuthResponseDTO.builder().token("jwt").build();
        when(loginTokenService.issue("learner")).thenReturn(response);
        assertSame(response, service.login(request));
        var order = inOrder(authenticationManager, loginTokenService);
        order.verify(authenticationManager).authenticate(any());
        order.verify(loginTokenService).issue("learner");
    }

    @Test
    void registrationCannotBypassCaptchaOrOtp() {
        RegisterRequestDTO r = new RegisterRequestDTO();
        r.setRecaptchaToken("bad");
        assertThrows(IllegalArgumentException.class, () ->
                service.initiateRegistration(r)
        );
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifiedRegistrationAlwaysCreatesStudent() {
        RegisterRequestDTO r = new RegisterRequestDTO();
        r.setUsername("learner");
        r.setEmail("l@example.com");
        r.setFullName("Learner");
        r.setPassword("already-hashed");
        OtpSessionData s = OtpSessionData.builder()
                .userData(r)
                .otpCode("123456")
                .expiryTime(LocalDateTime.now().plusMinutes(1))
                .build();
        when(cacheManager.getCache("otpCache")).thenReturn(cache);
        when(cache.get("l@example.com", OtpSessionData.class)).thenReturn(s);
        OtpVerifyRequestDTO req = new OtpVerifyRequestDTO();
        req.setEmail("l@example.com");
        req.setOtp("123456");
        service.verifyRegistration(req);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Role.STUDENT, saved.getValue().getRole());
        assertEquals("already-hashed", saved.getValue().getPassword());
        verify(cache).evict("l@example.com");
    }

    @Test
    void fifthWrongOtpInvalidatesSession() {
        OtpSessionData s = OtpSessionData.builder()
                .otpCode("123456")
                .attempts(4)
                .expiryTime(LocalDateTime.now().plusMinutes(1))
                .build();
        when(cacheManager.getCache("otpCache")).thenReturn(cache);
        when(cache.get("l@example.com", OtpSessionData.class)).thenReturn(s);
        OtpVerifyRequestDTO r = new OtpVerifyRequestDTO();
        r.setEmail("l@example.com");
        r.setOtp("000000");
        assertThrows(IllegalArgumentException.class, () ->
                service.verifyRegistration(r)
        );
        verify(cache).evict("l@example.com");
        verify(userRepository, never()).save(any());
    }
}
