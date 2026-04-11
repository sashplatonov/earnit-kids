package com.sashplatonov.earnit.kids.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CookieBuilderTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-for-unit-tests", new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void buildAuthCookiesIncludesExpectedCookiesInDevMode() {
        CookieBuilder builder = new CookieBuilder(jwtService, false);

        List<String> cookies = builder.buildAuthCookies("a@test.com", "admin", "fam-1", 10, 3600);

        assertThat(cookies).hasSize(5);
        assertThat(cookies.get(0)).contains("app_auth=").contains("HttpOnly").contains("SameSite=Lax");
        assertThat(cookies.get(1)).contains("app_role=admin");
        assertThat(cookies.get(2)).contains("csrf_token=").contains("SameSite=Strict");
        assertThat(cookies.get(3)).contains("family_id=fam-1");
        assertThat(cookies.get(4)).contains("child_id=10");
    }

    @Test
    void buildAuthCookiesAddsSecureFlagInProduction() {
        CookieBuilder builder = new CookieBuilder(jwtService, true);

        List<String> cookies = builder.buildAuthCookies("a@test.com", "child", "fam-1", null, 3600);

        assertThat(cookies).allSatisfy(cookie -> assertThat(cookie).contains("Secure"));
    }

    @Test
    void authTokenCanBeVerifiedFromCookieValue() {
        CookieBuilder builder = new CookieBuilder(jwtService, false);

        List<String> cookies = builder.buildAuthCookies("a@test.com", "admin", "fam-1", 5, 3600);
        String appAuth = cookies.stream().filter(v -> v.startsWith("app_auth=")).findFirst().orElseThrow();
        String token = appAuth.substring("app_auth=".length(), appAuth.indexOf(';'));

        Map<String, Object> payload = jwtService.verifyToken(token);

        assertThat(payload).isNotNull();
        assertThat(payload.get("email")).isEqualTo("a@test.com");
        assertThat(payload.get("role")).isEqualTo("admin");
        assertThat(payload.get("familyId")).isEqualTo("fam-1");
        assertThat(payload.get("childId")).isEqualTo(5);
        assertThat(payload).containsKey("csrfToken");
    }

    @Test
    void buildLogoutCookiesClearsAllKnownCookies() {
        CookieBuilder builder = new CookieBuilder(jwtService, false);

        List<String> cookies = builder.buildLogoutCookies();

        assertThat(cookies).hasSize(5);
        assertThat(cookies).allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
    }
}
