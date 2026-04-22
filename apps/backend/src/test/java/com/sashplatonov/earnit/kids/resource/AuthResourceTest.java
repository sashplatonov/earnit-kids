package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.config.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.request.ChangePasswordRequest;
import com.sashplatonov.earnit.kids.dto.request.ForgotPasswordRequest;
import com.sashplatonov.earnit.kids.dto.request.GoogleLoginRequest;
import com.sashplatonov.earnit.kids.dto.request.LoginChildRequest;
import com.sashplatonov.earnit.kids.dto.request.LoginRequest;
import com.sashplatonov.earnit.kids.dto.request.RegisterRequest;
import com.sashplatonov.earnit.kids.dto.request.ResetPasswordRequest;
import com.sashplatonov.earnit.kids.dto.request.VerifyEmailRequest;
import com.sashplatonov.earnit.kids.dto.response.AuthConfigResponse;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.service.AuthService;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        appConfig = TestConfigFactory.appConfig(false, null, null, true, true);
        resource = new AuthResource(authService, cookieBuilder, appConfig);
    }

    @Test
    void login_validAdminCredentials_returnsCookies() {
        AuthPayload payload = new AuthPayload("fam-1", "a@test.com", "admin", null, null);
        when(authService.authenticateAdmin("a@test.com", "secret"))
            .thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "admin", "fam-1", null))
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
    void loginGoogle_validParentCredential_returnsCookies() {
        AuthPayload payload = new AuthPayload("fam-1", "a@test.com", "admin", null, null);
        when(authService.authenticateAdminWithGoogle("google-token"))
            .thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "admin", "fam-1", null))
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
    void loginChild_validToken_returnsCookies() {
        AuthPayload payload = new AuthPayload("fam-1", "a@test.com", "child", 10, "Kid");
        when(authService.authenticateChild("token")).thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "child", "fam-1", 10))
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
        AuthPayload payload = new AuthPayload("fam-1", "a@test.com", "admin", null, null);
        when(authService.registerFamily("a@test.com", "secret123")).thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "admin", "fam-1", null))
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
    void forgotPassword_anyEmail_returnsOk() {
        Response response = resource.forgotPassword(new ForgotPasswordRequest("a@test.com"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(authService).forgotPassword("a@test.com");
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
    void resetPasswordAndVerifyEmail_successfulServiceResults_returnOk() {
        when(authService.resetPassword("a@test.com", "token", "newpass"))
            .thenReturn(OperationResult.success(null));
        when(authService.verifyEmail("a@test.com", "token"))
            .thenReturn(OperationResult.success(null));

        Response reset = resource.resetPassword(new ResetPasswordRequest("a@test.com", "token", "newpass"));
        Response verify = resource.verifyEmail(new VerifyEmailRequest("a@test.com", "token"));

        assertThat(reset.getStatus()).isEqualTo(200);
        assertThat(verify.getStatus()).isEqualTo(200);
    }

    @Test
    void authConfig_featureFlagsConfigured_returnsResponse() {
        Response response = resource.authConfig();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isNotNull();
    }

    @Test
    void authConfig_googleFeatureEnabled_exposesClientId() {
        resource = new AuthResource(
            authService,
            cookieBuilder,
            TestConfigFactory.appConfig(false, null, null, true, true, true, "google-client-id", "google-client-secret"));

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
            TestConfigFactory.appConfig(false, null, null, true, true, true, "google-client-id"));

        Response response = resource.authConfig();

        assertThat(response.getStatus()).isEqualTo(200);
        AuthConfigResponse config = (AuthConfigResponse) response.getEntity();
        assertThat(config.googleEnabled()).isFalse();
        assertThat(config.googleClientId()).isNull();
    }

    @Test
    void loginGoogleUrl_googleFeatureEnabled_returnsAuthorizationUrlAndStateCookie() {
        resource = new AuthResource(
            authService,
            cookieBuilder,
            TestConfigFactory.appConfig(false, null, null, true, true, true, "google-client-id", "google-client-secret"));

        Response response = resource.loginGoogleUrl("/en/app");

        assertThat(response.getStatus()).isEqualTo(200);
        List<?> cookies = response.getHeaders().get("Set-Cookie");
        assertThat(cookies).hasSize(1);
        assertThat(String.valueOf(cookies.get(0))).contains("oauth_state=");
        Map<?, ?> payload = (Map<?, ?>) response.getEntity();
        assertThat(payload.containsKey("url")).isTrue();
        assertThat(String.valueOf(payload.get("url"))).contains("https://accounts.google.com/o/oauth2/v2/auth");
    }

    @Test
    void loginGoogleUrl_prefersExplicitRedirectUriEnv() {
        resource = new AuthResource(
            authService,
            cookieBuilder,
            TestConfigFactory.appConfig(
                false,
                null,
                null,
                true,
                true,
                true,
                "google-client-id",
                "google-client-secret",
                "https://auth.example.com/api/login-google/callback",
                2592000,
                7776000));

        Response response = resource.loginGoogleUrl("/en/app");

        Map<?, ?> payload = (Map<?, ?>) response.getEntity();
        assertThat(String.valueOf(payload.get("url")))
            .contains("redirect_uri=https%3A%2F%2Fauth.example.com%2Fapi%2Flogin-google%2Fcallback");
    }

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }

    private static AuthContext adminAuth() {
        return new AuthContext("fam-1", null, "admin", "admin@test.com", "csrf");
    }

    private static AuthContext childAuth(int childId) {
        return new AuthContext("fam-1", childId, "child", "child@test.com", "csrf");
    }
}
