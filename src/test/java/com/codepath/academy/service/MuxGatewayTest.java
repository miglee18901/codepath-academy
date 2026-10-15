package com.codepath.academy.service;

import com.codepath.academy.config.MuxProperties;
import io.jsonwebtoken.Jwts;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MuxGatewayTest {
    MuxProperties properties;
    MuxGateway gateway;
    MockRestServiceServer server;
    KeyPair keys;

    @BeforeEach
    void setup() throws Exception {
        properties = new MuxProperties();
        properties.setTokenId("test-token");
        properties.setTokenSecret("test-secret");
        properties.setSigningKeyId("test-signing-key");
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";
        properties.setSigningPrivateKey(Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII)));
        var builder = RestClient.builder().baseUrl("https://api.mux.com/video/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new MuxGateway(properties, "http://localhost:5173", builder.build());
    }

    @Test
    void createsPrivateBasicUploadWithExactBrowserOrigin() {
        server.expect(requestTo("https://api.mux.com/video/v1/uploads"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder().encodeToString("test-token:test-secret".getBytes(StandardCharsets.US_ASCII))))
                .andExpect(content().json("""
                        {"cors_origin":"http://localhost:5173","timeout":3600,
                         "new_asset_settings":{"playback_policies":["signed"],"video_quality":"basic","passthrough":"lesson:3"}}
                        """))
                .andRespond(withSuccess("{\"data\":{\"id\":\"upload-1\",\"url\":\"https://storage.example/upload\"}}", MediaType.APPLICATION_JSON));
        assertEquals("upload-1", gateway.createUpload(3L).get("id"));
        server.verify();
    }

    @Test
    void signsSeparateShortLivedTokensForEachResource() {
        Instant expires = Instant.now().plusSeconds(3600);
        var tokens = gateway.tokens("private-playback", expires);
        for (var entry : java.util.Map.of("playback", "v", "thumbnail", "t", "storyboard", "s").entrySet()) {
            var jwt = Jwts.parser().verifyWith(keys.getPublic()).build().parseSignedClaims(tokens.get(entry.getKey()));
            assertEquals("RS256", jwt.getHeader().getAlgorithm());
            assertEquals("test-signing-key", jwt.getHeader().getKeyId());
            assertEquals("private-playback", jwt.getPayload().getSubject());
            assertTrue(jwt.getPayload().getAudience().contains(entry.getValue()));
            assertEquals(expires.getEpochSecond(), jwt.getPayload().getExpiration().toInstant().getEpochSecond());
        }
    }

    @Test
    void acceptsMuxPkcs1RsaPrivateKey() {
        // The generated PKCS#8 key has a 26-byte wrapper before its PKCS#1 payload.
        byte[] pkcs8 = keys.getPrivate().getEncoded();
        byte[] pkcs1 = java.util.Arrays.copyOfRange(pkcs8, 26, pkcs8.length);
        String pem = "-----BEGIN RSA PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pkcs1)
                + "\n-----END RSA PRIVATE KEY-----";
        properties.setSigningPrivateKey(Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII)));
        String token = gateway.tokens("private-playback", Instant.now().plusSeconds(60)).get("playback");
        assertEquals("private-playback", Jwts.parser().verifyWith(keys.getPublic()).build()
                .parseSignedClaims(token).getPayload().getSubject());
    }

    @Test
    void missingOrInvalidConfigurationFailsBeforeCallingMux() {
        properties.setTokenSecret("");
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> gateway.createUpload(3L)).getStatusCode().value());
        properties.setTokenSecret("secret");
        properties.setSigningPrivateKey("invalid-key");
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> gateway.createUpload(3L)).getStatusCode().value());
        server.verify();
    }

    @Test
    void providerErrorsDoNotExposeSecretsOrProviderBody() {
        server.expect(requestTo("https://api.mux.com/video/v1/uploads/upload-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("sensitive provider body"));
        var error = assertThrows(ResponseStatusException.class, () -> gateway.upload("upload-1"));
        assertEquals(502, error.getStatusCode().value());
        assertFalse(error.getReason().contains("sensitive"));
        server.verify();
    }
}
