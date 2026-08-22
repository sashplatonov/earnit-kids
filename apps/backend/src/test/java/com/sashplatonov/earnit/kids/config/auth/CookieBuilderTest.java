package com.sashplatonov.earnit.kids.config.auth;

import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CookieBuilderTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-04-16T12:00:00Z");

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
            TestConfigFactory.jwtConfig("test-secret-key-for-unit-tests"),
            new com.fasterxml.jackson.databind.ObjectMapper(),
            new SecureTokenGenerator(),
            TestConfigFactory.timeProvider(FIXED_NOW));
    }

    @Test
    void buildAuthCookies_developmentProfile_includesExpectedCookies() {
        CookieBuilder builder = new CookieBuilder(
            jwtService,
            TestConfigFactory.appConfig(false, null, true, true));

        List<String> cookies = builder.buildAuthCookies("a@test.com", "admin", "fam-1", 10, "family_admin");

        assertThat(cookies).hasSize(6);
        assertThat(cookies.get(0)).contains("app_auth=").contains("HttpOnly").contains("SameSite=Lax");
        assertThat(cookies.get(0)).contains("Max-Age=2592000");
        assertThat(cookies.get(1)).contains("app_refresh=").contains("HttpOnly").contains("Max-Age=7776000");
        assertThat(cookies.get(2)).contains("app_role=admin");
        assertThat(cookies.get(3)).contains("csrf_token=").contains("SameSite=Strict");
        assertThat(cookies.get(4)).contains("family_id=fam-1");
        assertThat(cookies.get(5)).contains("child_id=10");
    }

    @Test
    void buildAuthCookies_productionProfile_addsSecureFlag() {
        CookieBuilder builder = new CookieBuilder(
            jwtService,
            TestConfigFactory.appConfig(true, null, true, true));

        List<String> cookies = builder.buildAuthCookies("a@test.com", "child", "fam-1", null, "child");

        assertThat(cookies).allSatisfy(cookie -> assertThat(cookie).contains("Secure"));
    }

    @Test
    void buildAuthCookies_childRole_doesNotExposeRoleCookie() {
        CookieBuilder builder = new CookieBuilder(
            jwtService,
            TestConfigFactory.appConfig(false, null, true, true));

        List<String> cookies = builder.buildAuthCookies("a@test.com", "child", "fam-1", null, "child");

        // app_role must not be present for child role (no client-readable role)
        assertThat(cookies).allSatisfy(cookie -> assertThat(cookie).doesNotContain("app_role="));
    }

    @Test
    void buildAuthCookies_signedToken_canBeVerified() {
        CookieBuilder builder = new CookieBuilder(
            jwtService,
            TestConfigFactory.appConfig(false, null, true, true));

        List<String> cookies = builder.buildAuthCookies("a@test.com", "admin", "fam-1", 5, "family_admin");
        String appAuth = cookies.stream().filter(v -> v.startsWith("app_auth=")).findFirst().orElseThrow();
        String token = appAuth.substring("app_auth=".length(), appAuth.indexOf(';'));

        var payload = jwtService.verifyToken(token);

        assertThat(payload).isPresent();
        assertThat(payload.orElseThrow()).containsEntry("email", "a@test.com");
        assertThat(payload.orElseThrow()).containsEntry("role", "admin");
        assertThat(payload.orElseThrow()).containsEntry("familyId", "fam-1");
        assertThat(payload.orElseThrow()).containsEntry("childId", 5);
        assertThat(payload.orElseThrow()).doesNotContainKey("isSuperAdmin");
        assertThat(payload.orElseThrow()).containsKey("csrfToken");
    }

    @Test
    void buildLogoutCookies_anyProfile_clearsAllKnownCookies() {
        CookieBuilder builder = new CookieBuilder(
            jwtService,
            TestConfigFactory.appConfig(false, null, true, true));

        List<String> cookies = builder.buildLogoutCookies();

        assertThat(cookies).hasSize(6);
        assertThat(cookies).allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
    }
}
