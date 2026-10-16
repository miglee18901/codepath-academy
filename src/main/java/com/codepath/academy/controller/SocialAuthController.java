package com.codepath.academy.controller;

import com.codepath.academy.dto.AuthResponseDTO;
import com.codepath.academy.security.SocialClientRegistrations;
import com.codepath.academy.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class SocialAuthController {
    private final SocialClientRegistrations clients;
    private final OAuthHandoffService handoff;
    private final SocialProfileService profiles;
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n", Long.class);

    public record Provider(String id, String name, boolean enabled) {}
    public record Exchange(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String code,
                           @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43,128}") String verifier) {}
    public record LinkInput(@Size(max = 64) String password) {}
    public record PasswordInput(@NotBlank @Size(min = 8, max = 64) String password) {}

    @GetMapping("/api/auth/oauth2/providers")
    public List<Provider> providers() {
        return List.of(new Provider("google", "Google", clients.findByRegistrationId("google") != null),
                new Provider("github", "GitHub", clients.findByRegistrationId("github") != null));
    }

    @PostMapping("/api/auth/oauth2/exchange")
    public ResponseEntity<AuthResponseDTO> exchange(@Valid @RequestBody Exchange input, HttpServletRequest request) {
        limit("ip:" + request.getRemoteAddr(), 60);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(handoff.exchange(input.code(), input.verifier()));
    }

    @GetMapping("/api/me/social-links")
    public List<SocialProfileService.Link> list(Principal principal) { return profiles.list(principal.getName()); }

    @PostMapping("/api/me/social-links/{provider}")
    public Map<String, String> link(Principal principal, @PathVariable String provider,
            @Valid @RequestBody LinkInput input,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (clients.findByRegistrationId(provider) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nhà cung cấp chưa được cấu hình.");
        limit("user:" + principal.getName(), 10);
        return Map.of("ticket", profiles.startLink(principal.getName(), provider, input.password(), authorization));
    }

    @PostMapping("/api/me/initial-password")
    public Map<String, Boolean> password(Principal principal, @Valid @RequestBody PasswordInput input,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        limit("user:" + principal.getName(), 10);
        profiles.setInitialPassword(principal.getName(), input.password(), authorization);
        return Map.of("success", true);
    }

    private void limit(String key, int max) {
        Long count = redis.execute(RATE, List.of("oauth:rate:" + key));
        if (count == null || count > max) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Quá nhiều yêu cầu. Vui lòng thử lại sau một phút.");
    }
}
