package com.sashplatonov.earnit.kids.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.config.JwtCompatibilityConfig;
import com.sashplatonov.earnit.kids.config.JwtCompatVerifier;
import com.sashplatonov.earnit.kids.dto.response.SessionPageDataResponse;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPageDataResourceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JwtCompatibilityConfig TEST_CONFIG = () -> "test-secret-key-for-unit-tests";
    private static final Instant FIXED_NOW = Instant.parse("2026-04-16T12:00:00Z");

    @Test
    void session_missingCookies_returnsUnauthenticatedSnapshot() {
        var verifier = new JwtCompatVerifier(TEST_CONFIG, OBJECT_MAPPER, TestConfigFactory.timeProvider(FIXED_NOW));
        var resource = new SessionPageDataResource(verifier);

        var response = resource.session(null);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(SessionPageDataResponse.unauthenticated());
    }

    @Test
    void session_validCompatJwt_returnsDecodedSnapshot() {
        var verifier = new JwtCompatVerifier(TEST_CONFIG, OBJECT_MAPPER, TestConfigFactory.timeProvider(FIXED_NOW));
        var resource = new SessionPageDataResource(verifier);

        String token = JwtCompatVerifier.sign(Map.of(
            "familyId", "family-1",
            "role", "admin",
            "email", "parent@example.com",
            "csrfToken", "csrf-123",
            "permission", "family_admin"
        ), "test-secret-key-for-unit-tests", 300, TestConfigFactory.timeProvider(FIXED_NOW));

        var response = resource.session("app_auth=" + token + "; csrf_token=csrf-123");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(new SessionPageDataResponse(
            true,
            "admin",
            "family-1",
            null,
            "parent@example.com",
            "csrf-123",
            "family_admin"
        ));
    }
}
