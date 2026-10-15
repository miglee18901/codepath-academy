package com.example.java_basic.dto;

import java.time.LocalDateTime;

import lombok.*;

@Data
@Builder
public class OtpSessionData {

    private RegisterRequestDTO userData;
    private String otpCode;
    private LocalDateTime expiryTime;
    private LocalDateTime sentAt;
    private int attempts;
}
