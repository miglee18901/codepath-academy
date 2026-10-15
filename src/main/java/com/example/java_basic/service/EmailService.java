package com.example.java_basic.service;

public interface EmailService {
    void sendOtpEmail(String to, String fullName, String otpCode);
}
