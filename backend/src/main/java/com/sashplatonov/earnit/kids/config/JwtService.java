package com.sashplatonov.earnit.kids.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JwtService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final SecureRandom secureRandom = new SecureRandom();

    private final JwtCompatibilityConfig jwtCompatibilityConfig;
    private final ObjectMapper objectMapper;

    public String signToken(Map<String, Object> payload, long expiresInSeconds) {
        try {
            var effectivePayload = new LinkedHashMap<>(payload);
            effectivePayload.put("exp", Instant.now().getEpochSecond() + expiresInSeconds);
            var headerJson = objectMapper.writeValueAsString(Map.of("alg", "HS256", "typ", "JWT"));
            var payloadJson = objectMapper.writeValueAsString(effectivePayload);
            var encodedHeader = base64UrlEncode(headerJson);
            var encodedPayload = base64UrlEncode(payloadJson);
            var signatureInput = encodedHeader + "." + encodedPayload;
            return signatureInput + "." + hmacSign(signatureInput);
        } catch (JsonProcessingException | GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    public Optional<Map<String, Object>> verifyToken(String token) {
        try {
            var parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            var signatureInput = parts[0] + "." + parts[1];
            var expectedSignature = hmacSign(signatureInput);
            if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            var payloadJson = new String(
                Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            var payload = objectMapper.readValue(payloadJson, MAP_TYPE);
            if (payload.get("exp") instanceof Number exp
                && exp.longValue() < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(payload);
        } catch (IllegalArgumentException | JsonProcessingException | GeneralSecurityException ex) {
            return Optional.empty();
        }
    }

    public String generateCsrfToken() {
        var bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hmacSign(String value) throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(jwtCompatibilityConfig.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64UrlEncode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
