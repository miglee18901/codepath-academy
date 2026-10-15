package com.codepath.academy.controller;

import com.codepath.academy.dto.*;
import com.codepath.academy.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record ResendInput(@NotBlank @Email String email) {
    }

    @PostMapping({"/register", "/register/init"})
    public Object initiateRegistration(
            @Valid @RequestBody RegisterRequestDTO request
    ) {
        authService.initiateRegistration(request);
        return Map.of("message", "Mã OTP đã được gửi đến email của bạn");
    }

    @PostMapping("/register/verify")
    public Object verify(@Valid @RequestBody OtpVerifyRequestDTO request) {
        authService.verifyRegistration(request);
        return Map.of("message", "Đăng ký tài khoản thành công");
    }

    @PostMapping("/register/resend")
    public Object resend(@Valid @RequestBody ResendInput request) {
        authService.resendOTP(request.email());
        return Map.of("message", "Mã OTP đã được gửi lại");
    }

    @PostMapping("/login")
    public AuthResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponseDTO refresh(
            @Valid @RequestBody RefreshTokenRequestDTO request
    ) {
        return authService.refreshToken(request);
    }
}
