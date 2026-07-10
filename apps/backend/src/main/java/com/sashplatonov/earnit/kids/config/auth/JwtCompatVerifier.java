package com.sashplatonov.earnit.kids.config.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.dto.response.SessionPageDataResponse;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JwtCompatVerifier {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final ObjectMapper SIGN_MAPPER = new ObjectMapper();
    private static final TimeProvider SYSTEM_TIME_PROVIDER = java.time.Instant::now;

    private final JwtCompatibilityConfig jwtCompatibilityConfig;
    private final ObjectMapper objectMapper;
    private final TimeProvider timeProvider;

    public SessionPageDataResponse readSession(String cookieHeader) {
        var payload = verifySessionCookie(cookieHeader);
        if (payload.isEmpty()) {
            return SessionPageDataResponse.unauthenticated();
        }

        var resolvedPayload = payload.get();
        var childId = toInteger(resolvedPayload.get("childId"));
        var cookieCsrfToken = readCookie(cookieHeader, "csrf_token");
        var payloadCsrfToken = toStringValue(resolvedPayload.get("csrfToken"));

        var role = toStringValue(resolvedPayload.get("role"));
        var isSuperAdmin = Boolean.TRUE.equals(resolvedPayload.get("isSuperAdmin"));
        if (isSuperAdmin && "admin".equals(role)) {
            role = "super_admin";
        }

        var permission = toStringValue(resolvedPayload.get("permission"));

        return new SessionPageDataResponse(
            true,
            role,
            toStringValue(resolvedPayload.get("familyId")),
            childId,
            toStringValue(resolvedPayload.get("email")),
            cookieCsrfToken != null ? cookieCsrfToken : payloadCsrfToken,
            permission
        );
    }

    public Optional<Map<String, Object>> verify(String token) {
        try {
            if (token == null || token.isBlank()) {
                return Optional.empty();
            }

            var parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            var signatureInput = parts[0] + "." + parts[1];
            var expectedSignature = signSegment(signatureInput, jwtCompatibilityConfig.secret());
            if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }

            var payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            var payload = objectMapper.readValue(payloadJson, MAP_TYPE);

            Number exp = payload.get("exp") instanceof Number expValue ? expValue : null;
            if (exp != null && exp.longValue() < timeProvider.currentEpochSecond()) {
                return Optional.empty();
            }

            return Optional.of(payload);
        } catch (IllegalArgumentException | JsonProcessingException | GeneralSecurityException ignored) {
            return Optional.empty();
        }
    }

    public static String sign(Map<String, Object> payload, String secret, long expiresInSeconds) {
        return sign(payload, secret, expiresInSeconds, SYSTEM_TIME_PROVIDER);
    }

    public static String sign(Map<String, Object> payload, String secret, long expiresInSeconds,
                              TimeProvider timeProvider) {
        try {
            var effectivePayload = new LinkedHashMap<>(payload);
            effectivePayload.put("exp", timeProvider.currentEpochSecond() + expiresInSeconds);

            var headerJson = SIGN_MAPPER.writeValueAsString(Map.of("alg", "HS256", "typ", "JWT"));
            var payloadJson = SIGN_MAPPER.writeValueAsString(effectivePayload);
            var encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            var encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            var signatureInput = encodedHeader + "." + encodedPayload;
            return signatureInput + "." + signSegment(signatureInput, secret);
        } catch (JsonProcessingException | GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to sign compat JWT", ex);
        }
    }

    private static String signSegment(String value, String secret) throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
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

    private Optional<Map<String, Object>> verifySessionCookie(String cookieHeader) {
        var accessToken = readCookie(cookieHeader, CookieBuilder.AUTH_COOKIE_NAME);
        var accessPayload = verify(accessToken);
        if (accessPayload.isPresent()) {
            return accessPayload;
        }

        return verify(readCookie(cookieHeader, CookieBuilder.REFRESH_COOKIE_NAME));
    }
}
