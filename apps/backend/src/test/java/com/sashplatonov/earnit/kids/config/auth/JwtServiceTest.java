package com.sashplatonov.earnit.kids.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-04-16T12:00:00Z");

    JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
            TestConfigFactory.jwtConfig("test-secret-key-for-unit-tests"),
            new ObjectMapper(),
            new SecureTokenGenerator(),
            TestConfigFactory.timeProvider(FIXED_NOW));
    }

    @Test
    void signToken_validPayload_verifiesSuccessfully() {
        Map<String, Object> payload = Map.of("email", "test@example.com", "role", "admin");
        String token = jwtService.signToken(payload, 3600);

        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);

        var verified = jwtService.verifyToken(token);
        assertThat(verified).isPresent();
        assertThat(verified.orElseThrow()).containsEntry("email", "test@example.com");
        assertThat(verified.orElseThrow()).containsEntry("role", "admin");
    }

    @Test
    void verifyToken_expiredToken_returnsEmptyOptional() {
        Map<String, Object> payload = Map.of("email", "test@example.com");
        String token = jwtService.signToken(payload, -1); // expired

        assertThat(jwtService.verifyToken(token)).isEmpty();
    }

    @Test
    void verifyToken_tamperedToken_returnsEmptyOptional() {
        Map<String, Object> payload = Map.of("email", "test@example.com");
        String token = jwtService.signToken(payload, 3600);

        // Tamper with the token
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThat(jwtService.verifyToken(tampered)).isEmpty();
    }

    @Test
    void verifyToken_malformedToken_returnsEmptyOptional() {
        assertThat(jwtService.verifyToken("not-a-jwt")).isEmpty();
        assertThat(jwtService.verifyToken("")).isEmpty();
        assertThat(jwtService.verifyToken("a.b")).isEmpty();
    }

    @Test
    void generateCsrfToken_multipleCalls_returnsUniqueHexValues() {
        String csrf1 = jwtService.generateCsrfToken();
        String csrf2 = jwtService.generateCsrfToken();

        assertThat(csrf1).hasSize(32);
        assertThat(csrf2).hasSize(32);
        assertThat(csrf1).isNotEqualTo(csrf2);
        assertThat(csrf1).matches("[0-9a-f]{32}");
    }
}
