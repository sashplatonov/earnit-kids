package com.sashplatonov.earnit.kids.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-for-unit-tests", new ObjectMapper());
    }

    @Test
    void shouldSignAndVerifyToken() {
        Map<String, Object> payload = Map.of("email", "test@example.com", "role", "admin");
        String token = jwtService.signToken(payload, 3600);

        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);

        Map<String, Object> verified = jwtService.verifyToken(token);
        assertThat(verified).isNotNull();
        assertThat(verified.get("email")).isEqualTo("test@example.com");
        assertThat(verified.get("role")).isEqualTo("admin");
    }

    @Test
    void shouldRejectExpiredToken() {
        Map<String, Object> payload = Map.of("email", "test@example.com");
        String token = jwtService.signToken(payload, -1); // expired

        Map<String, Object> result = jwtService.verifyToken(token);
        assertThat(result).isNull();
    }

    @Test
    void shouldRejectTamperedToken() {
        Map<String, Object> payload = Map.of("email", "test@example.com");
        String token = jwtService.signToken(payload, 3600);

        // Tamper with the token
        String tampered = token.substring(0, token.length() - 2) + "xx";
        Map<String, Object> result = jwtService.verifyToken(tampered);
        assertThat(result).isNull();
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThat(jwtService.verifyToken("not-a-jwt")).isNull();
        assertThat(jwtService.verifyToken("")).isNull();
        assertThat(jwtService.verifyToken("a.b")).isNull();
    }

    @Test
    void shouldGenerateCsrfToken() {
        String csrf1 = jwtService.generateCsrfToken();
        String csrf2 = jwtService.generateCsrfToken();

        assertThat(csrf1).hasSize(32);
        assertThat(csrf2).hasSize(32);
        assertThat(csrf1).isNotEqualTo(csrf2);
        assertThat(csrf1).matches("[0-9a-f]{32}");
    }
}
