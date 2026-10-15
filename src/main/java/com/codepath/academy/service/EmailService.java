package com.codepath.academy.service;

public interface EmailService {
    void sendOtpEmail(String to, String fullName, String otpCode);
}
