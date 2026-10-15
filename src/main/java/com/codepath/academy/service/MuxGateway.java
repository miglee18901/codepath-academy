package com.codepath.academy.service;

import com.codepath.academy.config.MuxProperties;
import io.jsonwebtoken.Jwts;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MuxGateway {
    private final MuxProperties properties;
    private final RestClient client;
    private final String frontendUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public MuxGateway(MuxProperties properties, @Value("${app.frontend-url}") String frontendUrl) {
        this(properties, frontendUrl, defaultClient());
    }

    MuxGateway(MuxProperties properties, String frontendUrl, RestClient client) {
        this.properties = properties;
        this.frontendUrl = frontendUrl;
        this.client = client;
    }

    private static RestClient defaultClient() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().baseUrl("https://api.mux.com/video/v1").requestFactory(factory).build();
    }

    public boolean configured() {
        return properties.configured();
    }

    public void requireConfigured() {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Chưa cấu hình Mux. Điền MUX_TOKEN_ID, MUX_TOKEN_SECRET, MUX_SIGNING_KEY_ID và MUX_SIGNING_PRIVATE_KEY trong .env.");
        privateKey(); // Reject invalid signing configuration before allocating an upload.
    }

    public Map<String, Object> createUpload(Long lessonId) {
        requireConfigured();
        return request(HttpMethod.POST, "/uploads", Map.of(
                "cors_origin", frontendUrl, "timeout", 3600,
                "new_asset_settings", Map.of("playback_policies", List.of("signed"),
                        "video_quality", "basic", "passthrough", "lesson:" + lessonId)));
    }

    public Map<String, Object> upload(String id) {
        return request(HttpMethod.GET, "/uploads/" + id, null);
    }

    public Map<String, Object> asset(String id) {
        return request(HttpMethod.GET, "/assets/" + id, null);
    }

    public void deleteAsset(String id) {
        request(HttpMethod.DELETE, "/assets/" + id, null);
    }

    public void cancelUpload(String id) {
        request(HttpMethod.PUT, "/uploads/" + id + "/cancel", null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(HttpMethod method, String path, Object body) {
        requireConfigured();
        try {
            var request = client.method(method).uri(path)
                    .headers(h -> h.setBasicAuth(properties.getTokenId(), properties.getTokenSecret()))
                    .contentType(MediaType.APPLICATION_JSON);
            if (body != null) request.body(body);
            var response = request.retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
            });
            return response == null ? Map.of() : (Map<String, Object>) response.getOrDefault("data", Map.of());
        } catch (RestClientResponseException e) {
            // A manually deleted asset or expired upload can still be detached safely.
            if (e.getStatusCode().value() == 404 || e.getStatusCode().value() == 410)
                return Map.of("status", "deleted");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Mux từ chối yêu cầu (HTTP " + e.getStatusCode().value() + "). Kiểm tra API token, quyền Video và giới hạn tài khoản.");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không kết nối được Mux. Vui lòng thử lại.");
        }
    }

    public Map<String, String> tokens(String playbackId, Instant expires) {
        requireConfigured();
        var key = privateKey();
        Map<String, String> result = new LinkedHashMap<>();
        Map.of("playback", "v", "thumbnail", "t", "storyboard", "s").forEach((name, audience) ->
                result.put(name, Jwts.builder().header().keyId(properties.getSigningKeyId()).and()
                        .subject(playbackId).claim("aud", audience)
                        .expiration(Date.from(expires)).signWith(key, Jwts.SIG.RS256).compact()));
        return result;
    }

    private PrivateKey privateKey() {
        try {
            String value = properties.getSigningPrivateKey().strip();
            String pem = value.startsWith("-----BEGIN") ? value
                    : new String(Base64.getDecoder().decode(value), StandardCharsets.US_ASCII);
            boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
            byte[] encoded = Base64.getDecoder().decode(pem
                    .replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""));
            if (pkcs1) {
                // Wrap the PKCS#1 RSA key returned by Mux in a PKCS#8 PrivateKeyInfo.
                byte[] algorithm = java.util.HexFormat.of().parseHex("020100300d06092a864886f70d0101010500");
                var content = new ByteArrayOutputStream();
                content.writeBytes(algorithm);
                content.writeBytes(der(4, encoded));
                encoded = der(48, content.toByteArray());
            }
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MUX_SIGNING_PRIVATE_KEY không hợp lệ. Dùng private key Base64 do Mux cung cấp.");
        }
    }

    private static byte[] der(int tag, byte[] value) {
        var result = new ByteArrayOutputStream();
        result.write(tag);
        if (value.length < 128) result.write(value.length);
        else {
            int bytes = (32 - Integer.numberOfLeadingZeros(value.length) + 7) / 8;
            result.write(128 | bytes);
            for (int i = bytes - 1; i >= 0; i--) result.write((value.length >> (8 * i)) & 255);
        }
        result.writeBytes(value);
        return result.toByteArray();
    }
}
