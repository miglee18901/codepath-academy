package com.codepath.academy.dto;

import com.codepath.academy.validator.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequestDTO {

    @NotBlank
    @Size(min = 3, max = 20)
    @UniqueUsername
    private String username;

    @NotBlank
    @Size(min = 8, max = 64)
    private String password;

    @NotBlank
    @Size(min = 2, max = 50)
    private String fullName;

    @NotBlank
    @Email
    @Size(max = 100)
    @UniqueEmail
    private String email;

    @NotBlank
    private String recaptchaToken;
}
