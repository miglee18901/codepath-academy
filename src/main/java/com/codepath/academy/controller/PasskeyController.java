package com.codepath.academy.controller;

import com.codepath.academy.dto.AuthResponseDTO;
import com.codepath.academy.service.PasskeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class PasskeyController {
    private final PasskeyService passkeys;
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n", Long.class);

    public record RegisterInput(@NotBlank @Size(max = 80) String label,
                                @NotBlank @Size(max = 64) String password) {}
    public record PasswordInput(@NotBlank @Size(max = 64) String password) {}
    public record FinishInput(@NotBlank @Pattern(regexp = "[a-f0-9-]{36}") String requestId,
                              @NotBlank @Size(max = 40000) String credential) {}

    @PostMapping("/api/auth/passkeys/options")
    public PasskeyService.Options loginOptions(HttpServletRequest request) {
        limit("ip:" + request.getRemoteAddr(), 60);
        return passkeys.startAuthentication();
    }

    @PostMapping("/api/auth/passkeys/verify")
    public AuthResponseDTO login(@Valid @RequestBody FinishInput input, HttpServletRequest request) {
        limit("ip:" + request.getRemoteAddr(), 60);
        return passkeys.finishAuthentication(input.requestId(), input.credential());
    }

    @GetMapping("/api/me/passkeys")
    public List<PasskeyService.Summary> list(Principal principal) {
        return passkeys.list(principal.getName());
    }

    @PostMapping("/api/me/passkeys/options")
    public PasskeyService.Options registerOptions(Principal principal, @Valid @RequestBody RegisterInput input) {
        limit("user:" + principal.getName(), 10);
        return passkeys.startRegistration(principal.getName(), input.password(), input.label());
    }

    @PostMapping("/api/me/passkeys/verify")
    public Map<String, Boolean> register(Principal principal, @Valid @RequestBody FinishInput input) {
        passkeys.finishRegistration(principal.getName(), input.requestId(), input.credential());
        return Map.of("success", true);
    }

    @DeleteMapping("/api/me/passkeys/{id}")
    public Map<String, Boolean> delete(Principal principal, @PathVariable Long id,
                                      @Valid @RequestBody PasswordInput input) {
        limit("user:" + principal.getName(), 10);
        passkeys.delete(principal.getName(), id, input.password());
        return Map.of("success", true);
    }

    private void limit(String key, int max) {
        Long count = redis.execute(LIMIT, List.of("passkey:rate:" + key));
        if (count > max) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Quá nhiều yêu cầu Passkey. Vui lòng thử lại sau một phút.");
    }
}
