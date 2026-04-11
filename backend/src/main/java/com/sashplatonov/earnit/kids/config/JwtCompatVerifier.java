package com.sashplatonov.earnit.kids.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.sashplatonov.earnit.kids.dto.response.SessionPageDataResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class JwtCompatVerifier {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final String jwtSecret;
    private final ObjectMapper objectMapper;

    @Inject
    public JwtCompatVerifier(
            @ConfigProperty(name = "compat.jwt.secret") String jwtSecret,
            ObjectMapper objectMapper) {
        this.jwtSecret = jwtSecret;
        this.objectMapper = objectMapper;
    }

    public SessionPageDataResponse readSession(String cookieHeader) {
        String token = readCookie(cookieHeader, "app_auth");
        if (token == null || token.isBlank()) {
            return SessionPageDataResponse.unauthenticated();
        }

        Map<String, Object> payload = verify(token);
        if (payload == null) {
            return SessionPageDataResponse.unauthenticated();
        }

        Integer childId = toInteger(payload.get("childId"));
        String cookieCsrfToken = readCookie(cookieHeader, "csrf_token");
        String payloadCsrfToken = toStringValue(payload.get("csrfToken"));

        return new SessionPageDataResponse(
            true,
            toStringValue(payload.get("role")),
            toStringValue(payload.get("familyId")),
            childId,
            toStringValue(payload.get("email")),
            cookieCsrfToken != null ? cookieCsrfToken : payloadCsrfToken
        );
    }

    public Map<String, Object> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String signatureInput = parts[0] + "." + parts[1];
            String expectedSignature = signSegment(signatureInput, jwtSecret);
            if (!expectedSignature.equals(parts[2])) {
                return null;
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(payloadJson, MAP_TYPE);

            Number exp = payload.get("exp") instanceof Number expValue ? expValue : null;
            if (exp != null && exp.longValue() < Instant.now().getEpochSecond()) {
                return null;
            }

            return payload;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String sign(Map<String, Object> payload, String secret, long expiresInSeconds) {
        try {
            Map<String, Object> effectivePayload = new LinkedHashMap<>(payload);
            effectivePayload.put("exp", Instant.now().getEpochSecond() + expiresInSeconds);

            ObjectMapper mapper = new ObjectMapper();
            String headerJson = mapper.writeValueAsString(Map.of("alg", "HS256", "typ", "JWT"));
            String payloadJson = mapper.writeValueAsString(effectivePayload);
            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signatureInput = encodedHeader + "." + encodedPayload;
            return signatureInput + "." + signSegment(signatureInput, secret);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign compat JWT", ex);
        }
    }

    private static String signSegment(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String readCookie(String cookieHeader, String name) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }

        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && parts[0].trim().equals(name)) {
                return parts[1].trim();
            }
        }

        return null;
    }
}