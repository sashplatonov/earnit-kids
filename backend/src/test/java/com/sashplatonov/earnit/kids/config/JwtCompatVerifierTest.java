package com.sashplatonov.earnit.kids.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.dto.response.SessionPageDataResponse;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCompatVerifierTest {

    private JwtCompatVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new JwtCompatVerifier(
            TestConfigFactory.jwtConfig("test-secret-key-for-unit-tests"),
            new ObjectMapper());
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
        ), "test-secret-key-for-unit-tests", 120);

        SessionPageDataResponse response = verifier.readSession("app_auth=" + token + "; csrf_token=cookie-csrf");

        assertThat(response.authenticated()).isTrue();
        assertThat(response.familyId()).isEqualTo("fam-1");
        assertThat(response.role()).isEqualTo("admin");
        assertThat(response.email()).isEqualTo("a@test.com");
        assertThat(response.childId()).isEqualTo(10);
        assertThat(response.csrfToken()).isEqualTo("cookie-csrf");
    }

    @Test
    void verify_malformedOrTamperedToken_returnsEmptyOptional() {
        assertThat(verifier.verify("bad")).isEmpty();

        String valid = JwtCompatVerifier.sign(Map.of("familyId", "fam-1"), "test-secret-key-for-unit-tests", 120);
        String tampered = valid.substring(0, valid.length() - 1) + "x";
        assertThat(verifier.verify(tampered)).isEmpty();
    }

    @Test
    void verify_expiredToken_returnsEmptyOptional() {
        String expired = JwtCompatVerifier.sign(Map.of("familyId", "fam-1"), "test-secret-key-for-unit-tests", -1);
        assertThat(verifier.verify(expired)).isEmpty();
    }

    @Test
    void verify_validToken_returnsDecodedPayload() {
        String valid = JwtCompatVerifier.sign(Map.of("familyId", "fam-1", "childId", "7"), "test-secret-key-for-unit-tests", 120);

        var payload = verifier.verify(valid);

        assertThat(payload).isPresent();
        assertThat(payload.orElseThrow()).containsEntry("familyId", "fam-1");
        assertThat(payload.orElseThrow()).containsEntry("childId", "7");
    }
}
