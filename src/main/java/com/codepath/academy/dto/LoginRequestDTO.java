package com.codepath.academy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequestDTO {

    @NotBlank(message = "{val.username.notblank}")
    @Size(min = 3, max = 20, message = "{val.username.size}")
    private String username;

    @NotBlank(message = "{val.password.notblank}")
    @Size(min = 3, max = 64, message = "{val.password.size}")
    private String password;
}
