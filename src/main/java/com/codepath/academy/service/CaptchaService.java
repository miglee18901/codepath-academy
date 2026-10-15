package com.codepath.academy.service;

public interface CaptchaService {
    boolean verifyToken(String token);
}
