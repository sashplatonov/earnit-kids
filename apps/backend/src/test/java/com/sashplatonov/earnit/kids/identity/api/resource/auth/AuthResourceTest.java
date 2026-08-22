package com.sashplatonov.earnit.kids.identity.api.resource.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.config.auth.CookieBuilder;
import com.sashplatonov.earnit.kids.config.auth.JwtService;
import com.sashplatonov.earnit.kids.identity.api.request.ChangePasswordRequest;
import com.sashplatonov.earnit.kids.identity.api.request.GoogleLoginRequest;
import com.sashplatonov.earnit.kids.identity.api.request.LoginChildRequest;
import com.sashplatonov.earnit.kids.identity.api.request.LoginRequest;
import com.sashplatonov.earnit.kids.identity.api.request.RegisterRequest;
import com.sashplatonov.earnit.kids.identity.api.request.SelectFamilyRequest;
import com.sashplatonov.earnit.kids.dto.response.AuthConfigResponse;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.identity.application.google.GoogleOAuthService;
import com.sashplatonov.earnit.kids.identity.application.google.GoogleTokenResponse;
import com.sashplatonov.earnit.kids.identity.application.auth.AuthService;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthResourceTest {

    @Mock AuthService authService;
    @Mock CookieBuilder cookieBuilder;

    private AuthResource resource;
    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        appConfig = TestConfigFactory.appConfig(false, null, true, true);
        resource = new AuthResource(authService, cookieBuilder, appConfig);
    }

    @Test
    void login_validAdminCredentials_returnsCookies() {
        AuthPayload payload = new AuthPayload("fam-1", "a@test.com", "admin", null, null, false, "family_admin", null, false);
        when(authService.authenticateAdmin("a@test.com", "secret"))
            .thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "admin", "fam-1", null, "family_admin"))
            .thenReturn(List.of("cookie-1", "cookie-2"));

        Response response = resource.login(new LoginRequest("a@test.com", "secret"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Set-Cookie")).hasSize(2);
    }

    @Test
    void login_invalidAdminCredentials_returnsUnauthorized() {
        when(authService.authenticateAdmin("a@test.com", "bad"))
            .thenReturn(OperationResult.failure("bad creds"));

        Response response = resource.login(new LoginRequest("a@test.com", "bad"));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void login_selectionRequired_returnsChooserPayloadWithoutCookies() {
        AuthPayload payload = new AuthPayload(
            null,
            "a@test.com",
            "admin",
            null,
            null,
            false,
            null,
            List.of(
                new AuthPayload.FamilyChoice("fam-1", "Family One", "family_admin", false),
                new AuthPayload.FamilyChoice("fam-2", "Family Two", "viewer", true)
            ),
            true);
        when(authService.authenticateAdmin("a@test.com", "secret"))
            .thenReturn(OperationResult.success(payload));

        Response response = resource.login(new LoginRequest("a@test.com", "secret"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Set-Cookie")).isNull();
        var entity = (com.sashplatonov.earnit.kids.dto.response.AuthResponse) response.getEntity();
        assertThat(entity.selectionRequired()).isTrue();
        assertThat(entity.familyChoices()).containsExactly(
            new com.sashplatonov.earnit.kids.dto.response.AuthResponse.FamilyChoice("fam-1", "Family One", "family_admin", false),
            new com.sashplatonov.earnit.kids.dto.response.AuthResponse.FamilyChoice("fam-2", "Family Two", "viewer", true)
        );
    }

    @Test
    void loginGoogle_validParentCredential_returnsCookies() {
        AuthPayload payload = new AuthPayload("fam-1", "a@test.com", "admin", null, null, false, "family_admin", null, false);
        when(authService.authenticateAdminWithGoogle("google-token"))
            .thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "admin", "fam-1", null, "family_admin"))
            .thenReturn(List.of("cookie-1"));

        Response response = resource.loginGoogle(new GoogleLoginRequest("google-token"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Set-Cookie")).hasSize(1);
    }

    @Test
    void loginGoogle_invalidCredential_returnsUnauthorized() {
        when(authService.authenticateAdminWithGoogle("bad-token"))
            .thenReturn(OperationResult.failure("bad creds"));

        Response response = resource.loginGoogle(new GoogleLoginRequest("bad-token"));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void selectFamily_validSelection_returnsCookies() {
        AuthPayload payload = new AuthPayload("fam-2", "a@test.com", "admin", null, null, false, "viewer", null, false);
        when(authService.selectFamily("a@test.com", "fam-2"))
            .thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "admin", "fam-2", null, "viewer"))
            .thenReturn(List.of("cookie-1"));

        Response response = resource.selectFamily(new SelectFamilyRequest("a@test.com", "fam-2"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Set-Cookie")).hasSize(1);
    }

    @Test
    void loginChild_validToken_returnsCookies() {
        AuthPayload payload = new AuthPayload("fam-1", "a@test.com", "child", 10, "Kid", false, "child", null, false);
        when(authService.authenticateChild("token")).thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "child", "fam-1", 10, "child"))
            .thenReturn(List.of("cookie-1"));

        Response response = resource.loginChild(new LoginChildRequest("token"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Set-Cookie")).hasSize(1);
        var entity = (com.sashplatonov.earnit.kids.dto.response.AuthResponse) response.getEntity();
        assertThat(entity.role()).isNull();
    }

    @Test
    void logout_existingSession_clearsCookies() {
        when(cookieBuilder.buildLogoutCookies()).thenReturn(List.of("cookie-1", "cookie-2", "cookie-3"));

        Response response = resource.logout();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Set-Cookie")).hasSize(3);
    }

    @Test
    void register_validPayload_returnsCreated() {
        AuthPayload payload = new AuthPayload("fam-1", "a@test.com", "admin", null, null, false, "family_admin", null, false);
        when(authService.registerFamily("a@test.com", "secret123")).thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "admin", "fam-1", null, "family_admin"))
            .thenReturn(List.of("cookie"));

        Response response = resource.register(new RegisterRequest("a@test.com", "secret123"));

        assertThat(response.getStatus()).isEqualTo(201);
    }

    @Test
    void register_duplicateEmail_returnsConflict() {
        when(authService.registerFamily("a@test.com", "secret123"))
            .thenReturn(OperationResult.failure("Email already exists"));

        Response response = resource.register(new RegisterRequest("a@test.com", "secret123"));

        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    void changePassword_missingAdminContext_returnsUnauthorized() {
        ChangePasswordRequest request = new ChangePasswordRequest("1", "2");
        Response unauthorized = resource.changePassword(contextWithAuth(null), request);
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        Response childUnauthorized = resource.changePassword(contextWithAuth(childAuth(10)), request);
        assertThat(childUnauthorized.getStatus()).isEqualTo(401);
    }

    @Test
    void changePassword_serviceResultMapped_returnsExpectedStatus() {
        when(authService.changeAdminPassword("fam-1", "old", "newpass"))
            .thenReturn(OperationResult.success(null));

        Response ok = resource.changePassword(contextWithAuth(adminAuth()), new ChangePasswordRequest("old", "newpass"));
        assertThat(ok.getStatus()).isEqualTo(200);

        when(authService.changeAdminPassword("fam-1", "old", "newpass"))
            .thenReturn(OperationResult.failure("bad"));
        Response bad = resource.changePassword(contextWithAuth(adminAuth()), new ChangePasswordRequest("old", "newpass"));
        assertThat(bad.getStatus()).isEqualTo(400);
    }

    @Test
    void authConfig_returnsGoogleConfiguration() {
        Response response = resource.authConfig();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isNotNull();
    }

    @Test
    void authConfig_googleFeatureEnabled_exposesClientId() {
        resource = new AuthResource(
            authService,
            cookieBuilder,
            TestConfigFactory.appConfig(false, null, true, true, true, "google-client-id", "google-client-secret"));

        Response response = resource.authConfig();

        assertThat(response.getStatus()).isEqualTo(200);
        AuthConfigResponse config = (AuthConfigResponse) response.getEntity();
        assertThat(config.googleEnabled()).isTrue();
        assertThat(config.googleClientId()).isEqualTo("google-client-id");
    }

    @Test
    void authConfig_googleMissingSecret_hidesGoogleOption() {
        resource = new AuthResource(
            authService,
            cookieBuilder,
            TestConfigFactory.appConfig(false, null, true, true, true, "google-client-id"));

        Response response = resource.authConfig();

        assertThat(response.getStatus()).isEqualTo(200);
        AuthConfigResponse config = (AuthConfigResponse) response.getEntity();
        assertThat(config.googleEnabled()).isFalse();
        assertThat(config.googleClientId()).isNull();
    }

    @Test
    void loginGoogleUrl_googleFeatureEnabled_returnsAuthorizationUrlAndStateCookie() {
        AppConfig config = TestConfigFactory.appConfig(
            false,
            null,
            true,
            true,
            true,
            "google-client-id",
            "google-client-secret");
        JwtService jwtService = testJwtService();
        AuthGoogleResource googleResource = new AuthGoogleResource(
            authService,
            cookieBuilder,
            config,
            new GoogleOAuthService(config, new ObjectMapper()),
            jwtService,
            java.util.Optional.of("https://app.example.com"));

        Response response = googleResource.loginGoogleUrl(null, "/en/telegram");

        assertThat(response.getStatus()).isEqualTo(200);
        List<?> cookies = response.getHeaders().get("Set-Cookie");
        assertThat(cookies).hasSize(1);
        assertThat(String.valueOf(cookies.get(0))).contains("oauth_state=");
        Map<String, Object> statePayload = jwtService.verifyToken(extractCookieValue(response, "oauth_state"))
            .orElseThrow();
        assertThat(statePayload.get("redirect")).isEqualTo("https://app.example.com/en/telegram");
        Map<?, ?> payload = (Map<?, ?>) response.getEntity();
        assertThat(payload.containsKey("url")).isTrue();
        assertThat(String.valueOf(payload.get("url"))).contains("https://accounts.google.com/o/oauth2/v2/auth");
    }

    @Test
    void loginGoogleUrl_prefersExplicitRedirectUriEnv() {
        AuthGoogleResource googleResource = new AuthGoogleResource(
            authService,
            cookieBuilder,
            TestConfigFactory.appConfig(
                false,
                null,
                true,
                true,
                true,
                "google-client-id",
                "google-client-secret",
                "https://auth.example.com/api/login-google/callback",
                2592000,
                7776000),
            new GoogleOAuthService(
                TestConfigFactory.appConfig(
                    false,
                    null,
                    true,
                    true,
                    true,
                    "google-client-id",
                    "google-client-secret",
                    "https://auth.example.com/api/login-google/callback",
                    2592000,
                    7776000),
                new ObjectMapper()),
            testJwtService(),
            java.util.Optional.of("https://app.example.com"));

        Response response = googleResource.loginGoogleUrl(null, "/en/telegram");

        Map<?, ?> payload = (Map<?, ?>) response.getEntity();
        assertThat(String.valueOf(payload.get("url")))
            .contains("redirect_uri=https%3A%2F%2Fauth.example.com%2Fapi%2Flogin-google%2Fcallback");
    }

    @Test
    void loginGoogleCallback_authenticationFailure_redirectsToConfiguredAppUrl() {
        AppConfig config = TestConfigFactory.appConfig(
            false,
            null,
            true,
            true,
            true,
            "google-client-id",
            "google-client-secret");
        GoogleOAuthService googleOAuthService = mock(GoogleOAuthService.class);
        JwtService jwtService = testJwtService();
        AuthGoogleResource googleResource = new AuthGoogleResource(
            authService,
            cookieBuilder,
            config,
            googleOAuthService,
            jwtService,
            java.util.Optional.of("https://app.example.com"));

        String state = jwtService.signToken(Map.of("redirect", "https://app.example.com/en/telegram"), 300);
        when(googleOAuthService.exchangeCode("valid-code", "https://app.example.com/api/login-google/callback"))
            .thenReturn(java.util.Optional.of(new GoogleTokenResponse(null, null, null, null, null, "google-id-token")));
        when(authService.authenticateAdminWithGoogle("google-id-token"))
            .thenReturn(OperationResult.failure("Account is blocked"));

        Response response = googleResource.loginGoogleCallback(
            null,
            "valid-code",
            state,
            state);

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getLocation().toString())
            .isEqualTo("https://app.example.com/en/telegram?error=authentication_failed");
    }

    @Test
    void loginGoogleCallback_missingLinkedFamily_redirectsToConfiguredAppUrl() {
        AppConfig config = TestConfigFactory.appConfig(
            false,
            null,
            true,
            true,
            true,
            "google-client-id",
            "google-client-secret");
        GoogleOAuthService googleOAuthService = mock(GoogleOAuthService.class);
        JwtService jwtService = testJwtService();
        AuthGoogleResource googleResource = new AuthGoogleResource(
            authService,
            cookieBuilder,
            config,
            googleOAuthService,
            jwtService,
            java.util.Optional.of("https://app.example.com"));

        String state = jwtService.signToken(Map.of("redirect", "https://app.example.com/en/telegram"), 300);
        when(googleOAuthService.exchangeCode("valid-code", "https://app.example.com/api/login-google/callback"))
            .thenReturn(java.util.Optional.of(new GoogleTokenResponse(null, null, null, null, null, "google-id-token")));
        when(authService.authenticateAdminWithGoogle("google-id-token"))
            .thenReturn(OperationResult.failure("No family account is linked to this Google email yet"));

        Response response = googleResource.loginGoogleCallback(
            null,
            "valid-code",
            state,
            state);

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getLocation().toString())
            .isEqualTo("https://app.example.com/en/telegram?error=authentication_failed");
    }

    @Test
    void loginGoogleCallback_selectionRequired_redirectsToLocalizedLoginWithChooserCookie() {
        AppConfig config = TestConfigFactory.appConfig(
            false,
            null,
            true,
            true,
            true,
            "google-client-id",
            "google-client-secret");
        GoogleOAuthService googleOAuthService = mock(GoogleOAuthService.class);
        JwtService jwtService = testJwtService();
        AuthGoogleResource googleResource = new AuthGoogleResource(
            authService,
            cookieBuilder,
            config,
            googleOAuthService,
            jwtService,
            java.util.Optional.of("https://app.example.com"));

        String state = jwtService.signToken(Map.of("redirect", "https://app.example.com/ru/telegram"), 300);
        when(googleOAuthService.exchangeCode("valid-code", "https://app.example.com/api/login-google/callback"))
            .thenReturn(java.util.Optional.of(new GoogleTokenResponse(null, null, null, null, null, "google-id-token")));
        when(authService.authenticateAdminWithGoogle("google-id-token"))
            .thenReturn(OperationResult.success(new AuthPayload(
                null,
                "a@test.com",
                "admin",
                null,
                null,
                false,
                null,
                List.of(
                    new AuthPayload.FamilyChoice("fam-1", "Family One", "family_admin", false),
                    new AuthPayload.FamilyChoice("fam-2", "Family Two", "viewer", true)
                ),
                true
            )));

        Response response = googleResource.loginGoogleCallback(
            null,
            "valid-code",
            state,
            state);

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getLocation().toString()).isEqualTo("https://app.example.com/ru/login");
        assertThat(String.valueOf(response.getHeaders().get("Set-Cookie"))).contains("pending_family_chooser=");
    }

    @Test
    void loginGoogleCallback_stateMismatch_withoutAppUrl_keepsRelativeRedirect() {
        AppConfig config = TestConfigFactory.appConfig(
            false,
            null,
            true,
            true,
            true,
            "google-client-id",
            "google-client-secret");
        AuthGoogleResource googleResource = new AuthGoogleResource(
            authService,
            cookieBuilder,
            config,
            mock(GoogleOAuthService.class),
            testJwtService(),
            java.util.Optional.empty());

        Response response = googleResource.loginGoogleCallback(
            null,
            "invalid",
            "state",
            "other-state");

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getLocation().toString()).isEqualTo("/?error=oauth_state_mismatch");
    }

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }

    private static JwtService testJwtService() {
        return new JwtService(
            TestConfigFactory.jwtConfig("test-secret"),
            new ObjectMapper(),
            new SecureTokenGenerator(),
            TestConfigFactory.timeProvider(Instant.parse("2026-04-22T10:00:00Z")));
    }

    private static String extractCookieValue(Response response, String cookieName) {
        Object cookieHeader = response.getHeaders().getFirst("Set-Cookie");
        assertThat(cookieHeader).isNotNull();
        String cookieString = String.valueOf(cookieHeader);
        String prefix = cookieName + "=";
        int start = cookieString.indexOf(prefix);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int valueStart = start + prefix.length();
        int valueEnd = cookieString.indexOf(';', valueStart);
        if (valueEnd < 0) {
            valueEnd = cookieString.length();
        }
        return cookieString.substring(valueStart, valueEnd);
    }

    private static AuthContext adminAuth() {
        return new AuthContext("fam-1", null, "admin", "admin@test.com", "csrf", false, "family_admin");
    }

    private static AuthContext childAuth(int childId) {
        return new AuthContext("fam-1", childId, "child", "child@test.com", "csrf", false, "child");
    }
}
