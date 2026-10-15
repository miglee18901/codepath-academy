package com.codepath.academy.service;

import com.codepath.academy.dto.AuthResponseDTO;
import com.codepath.academy.dto.LoginRequestDTO;
import com.codepath.academy.dto.OtpVerifyRequestDTO;
import com.codepath.academy.dto.RefreshTokenRequestDTO;
import com.codepath.academy.dto.RegisterRequestDTO;
import jakarta.validation.Valid;

public interface AuthService {
    void initiateRegistration(RegisterRequestDTO request);

    void verifyRegistration(OtpVerifyRequestDTO request);

    AuthResponseDTO login(LoginRequestDTO request);

    AuthResponseDTO refreshToken(RefreshTokenRequestDTO request);

    void resendOTP(@Valid String email);
}
