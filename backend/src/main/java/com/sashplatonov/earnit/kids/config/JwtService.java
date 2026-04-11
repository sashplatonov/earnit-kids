package com.sashplatonov.earnit.kids.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class JwtService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String jwtSecret;
    private final ObjectMapper objectMapper;

    @Inject
    public JwtService(
            @ConfigProperty(name = "compat.jwt.secret") String jwtSecret,
            ObjectMapper objectMapper) {
        this.jwtSecret = jwtSecret;
        this.objectMapper = objectMapper;
    }

    /**
     * Signs a JWT token with the given payload and expiration.
     *
     * @param payload the claims to encode
     * @param expiresInSeconds token lifetime in seconds
     * @return the signed JWT string
     */
    public String signToken(Map<String, Object> payload, long expiresInSeconds) {
        try {
            Map<String, Object> effectivePayload = new LinkedHashMap<>(payload);
            effectivePayload.put("exp", Instant.now().getEpochSecond() + expiresInSeconds);
            String headerJson = objectMapper.writeValueAsString(Map.of("alg", "HS256", "typ", "JWT"));
            String payloadJson = objectMapper.writeValueAsString(effectivePayload);
            String encodedHeader = base64UrlEncode(headerJson);
            String encodedPayload = base64UrlEncode(payloadJson);
            String signatureInput = encodedHeader + "." + encodedPayload;
            return signatureInput + "." + hmacSign(signatureInput);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    /**
     * Verifies a JWT token and returns its payload, or null if invalid.
     *
     * @param token the JWT string
     * @return the decoded payload map, or null
     */
    public Map<String, Object> verifyToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String signatureInput = parts[0] + "." + parts[1];
            String expectedSignature = hmacSign(signatureInput);
            if (!expectedSignature.equals(parts[2])) {
                return null;
            }
            String payloadJson = new String(
                Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(payloadJson, MAP_TYPE);
            if (payload.get("exp") instanceof Number exp
                && exp.longValue() < Instant.now().getEpochSecond()) {
                return null;
            }
            return payload;
        } catch (Exception _) {
            return null;
        }
    }

    /**
     * Generates a random CSRF token (32 hex characters).
     *
     * @return hex-encoded random string
     */
    public String generateCsrfToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String hmacSign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64UrlEncode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
