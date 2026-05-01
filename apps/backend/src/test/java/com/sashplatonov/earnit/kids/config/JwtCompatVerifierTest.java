package com.sashplatonov.earnit.kids.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.dto.response.SessionPageDataResponse;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCompatVerifierTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-04-16T12:00:00Z");

    private JwtCompatVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new JwtCompatVerifier(
            TestConfigFactory.jwtConfig("test-secret-key-for-unit-tests"),
            new ObjectMapper(),
            TestConfigFactory.timeProvider(FIXED_NOW));
    }

    @Test
    void readSession_missingToken_returnsUnauthenticatedSnapshot() {
        SessionPageDataResponse response = verifier.readSession(null);
        assertThat(response.authenticated()).isFalse();
    }

    @Test
    void readSession_invalidToken_returnsUnauthenticatedSnapshot() {
        SessionPageDataResponse response = verifier.readSession("app_auth=broken; csrf_token=x");
        assertThat(response.authenticated()).isFalse();
    }

    @Test
    void readSession_cookieCsrfPresent_prefersCookieValue() {
        String token = JwtCompatVerifier.sign(Map.of(
            "familyId", "fam-1",
            "role", "admin",
            "email", "a@test.com",
            "csrfToken", "payload-csrf",
            "childId", 10
        ), "test-secret-key-for-unit-tests", 120, TestConfigFactory.timeProvider(FIXED_NOW));

        SessionPageDataResponse response = verifier.readSession("app_auth=" + token + "; csrf_token=cookie-csrf");

        assertThat(response.authenticated()).isTrue();
        assertThat(response.familyId()).isEqualTo("fam-1");
        assertThat(response.role()).isEqualTo("admin");
        assertThat(response.email()).isEqualTo("a@test.com");
        assertThat(response.childId()).isEqualTo(10);
        assertThat(response.csrfToken()).isEqualTo("cookie-csrf");
    }

    @Test
    void readSession_fallsBackToRefreshToken() {
        String refreshToken = JwtCompatVerifier.sign(Map.of(
            "familyId", "fam-1",
            "role", "admin",
            "email", "a@test.com",
            "csrfToken", "payload-csrf"
        ), "test-secret-key-for-unit-tests", 120, TestConfigFactory.timeProvider(FIXED_NOW));

        SessionPageDataResponse response = verifier.readSession("app_refresh=" + refreshToken + "; csrf_token=cookie-csrf");

        assertThat(response.authenticated()).isTrue();
        assertThat(response.familyId()).isEqualTo("fam-1");
        assertThat(response.role()).isEqualTo("admin");
        assertThat(response.email()).isEqualTo("a@test.com");
        assertThat(response.csrfToken()).isEqualTo("cookie-csrf");
    }

    @Test
    void verify_malformedOrTamperedToken_returnsEmptyOptional() {
        assertThat(verifier.verify("bad")).isEmpty();

        String valid = JwtCompatVerifier.sign(
            Map.of("familyId", "fam-1"),
            "test-secret-key-for-unit-tests",
            120,
            TestConfigFactory.timeProvider(FIXED_NOW));
        String tampered = valid.substring(0, valid.length() - 1) + "x";
        assertThat(verifier.verify(tampered)).isEmpty();
    }

    @Test
    void verify_expiredToken_returnsEmptyOptional() {
        String expired = JwtCompatVerifier.sign(
            Map.of("familyId", "fam-1"),
            "test-secret-key-for-unit-tests",
            -1,
            TestConfigFactory.timeProvider(FIXED_NOW));
        assertThat(verifier.verify(expired)).isEmpty();
    }

    @Test
    void verify_validToken_returnsDecodedPayload() {
        String valid = JwtCompatVerifier.sign(
            Map.of("familyId", "fam-1", "childId", "7"),
            "test-secret-key-for-unit-tests",
            120,
            TestConfigFactory.timeProvider(FIXED_NOW));

        var payload = verifier.verify(valid);

        assertThat(payload).isPresent();
        assertThat(payload.orElseThrow()).containsEntry("familyId", "fam-1");
        assertThat(payload.orElseThrow()).containsEntry("childId", "7");
    }

    @Test
    void readSession_isSuperAdminTrue_upgradesRoleToSuperAdmin() {
        String token = JwtCompatVerifier.sign(Map.of(
            "familyId", "fam-1",
            "role", "admin",
            "email", "super@test.com",
            "csrfToken", "csrf-123",
            "isSuperAdmin", true
        ), "test-secret-key-for-unit-tests", 120, TestConfigFactory.timeProvider(FIXED_NOW));

        SessionPageDataResponse response = verifier.readSession("app_auth=" + token + "; csrf_token=cookie-csrf");

        assertThat(response.authenticated()).isTrue();
        assertThat(response.role()).isEqualTo("super_admin");
        assertThat(response.email()).isEqualTo("super@test.com");
    }

    @Test
    void readSession_isSuperAdminFalse_keepsAdminRole() {
        String token = JwtCompatVerifier.sign(Map.of(
            "familyId", "fam-1",
            "role", "admin",
            "email", "regular@test.com",
            "csrfToken", "csrf-123",
            "isSuperAdmin", false
        ), "test-secret-key-for-unit-tests", 120, TestConfigFactory.timeProvider(FIXED_NOW));

        SessionPageDataResponse response = verifier.readSession("app_auth=" + token + "; csrf_token=cookie-csrf");

        assertThat(response.authenticated()).isTrue();
        assertThat(response.role()).isEqualTo("admin");
    }

    @Test
    void readSession_isSuperAdminMissing_keepsAdminRole() {
        String token = JwtCompatVerifier.sign(Map.of(
            "familyId", "fam-1",
            "role", "admin",
            "email", "regular@test.com",
            "csrfToken", "csrf-123"
        ), "test-secret-key-for-unit-tests", 120, TestConfigFactory.timeProvider(FIXED_NOW));

        SessionPageDataResponse response = verifier.readSession("app_auth=" + token + "; csrf_token=cookie-csrf");

        assertThat(response.authenticated()).isTrue();
        assertThat(response.role()).isEqualTo("admin");
    }

    @Test
    void verify_tamperedIsSuperAdminInPayload_rejectsToken() {
        // Create a valid token without isSuperAdmin
        String valid = JwtCompatVerifier.sign(Map.of(
            "familyId", "fam-1",
            "role", "admin",
            "email", "regular@test.com",
            "csrfToken", "csrf-123"
        ), "test-secret-key-for-unit-tests", 120, TestConfigFactory.timeProvider(FIXED_NOW));

        // Tamper with the payload: replace the base64 payload with one that has isSuperAdmin=true
        // The token format is: base64(header).base64(payload).signature
        String[] parts = valid.split("\\.");
        String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"familyId\":\"fam-1\",\"role\":\"admin\",\"email\":\"regular@test.com\",\"csrfToken\":\"csrf-123\",\"isSuperAdmin\":true,\"exp\":9999999999}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        // The tampered token should be rejected because the signature no longer matches
        assertThat(verifier.verify(tamperedToken)).isEmpty();
    }
}
