package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.config.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.request.ForgotPasswordRequest;
import com.sashplatonov.earnit.kids.dto.request.LoginChildRequest;
import com.sashplatonov.earnit.kids.dto.request.LoginRequest;
import com.sashplatonov.earnit.kids.dto.request.RegisterRequest;
import com.sashplatonov.earnit.kids.dto.request.ResetPasswordRequest;
import com.sashplatonov.earnit.kids.dto.request.VerifyEmailRequest;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.service.AuthService;
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

    @BeforeEach
    void setUp() {
        resource = new AuthResource(authService, cookieBuilder, true, true);
    }

    @Test
    void loginReturnsCookiesOnSuccess() {
        AuthPayload payload = new AuthPayload("fam-1", "a@test.com", "admin", null, null);
        when(authService.authenticateAdmin("a@test.com", "secret"))
            .thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "admin", "fam-1", null, 2592000))
            .thenReturn(List.of("cookie-1", "cookie-2"));

        Response response = resource.login(new LoginRequest("a@test.com", "secret"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Set-Cookie")).hasSize(2);
    }

    @Test
    void loginReturnsUnauthorizedOnFailure() {
        when(authService.authenticateAdmin("a@test.com", "bad"))
            .thenReturn(OperationResult.failure("bad creds"));

        Response response = resource.login(new LoginRequest("a@test.com", "bad"));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void loginChildReturnsPayloadOnSuccess() {
        AuthPayload payload = new AuthPayload("fam-1", "a@test.com", "child", 10, "Kid");
        when(authService.authenticateChild("token")).thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "child", "fam-1", 10, 2592000))
            .thenReturn(List.of("cookie-1"));

        Response response = resource.loginChild(new LoginChildRequest("token"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Set-Cookie")).hasSize(1);
    }

    @Test
    void logoutClearsCookies() {
        when(cookieBuilder.buildLogoutCookies()).thenReturn(List.of("cookie-1", "cookie-2", "cookie-3"));

        Response response = resource.logout();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Set-Cookie")).hasSize(3);
    }

    @Test
    void registerReturnsCreatedOnSuccess() {
        AuthPayload payload = new AuthPayload("fam-1", "a@test.com", "admin", null, null);
        when(authService.registerFamily("a@test.com", "secret123")).thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("a@test.com", "admin", "fam-1", null, 2592000))
            .thenReturn(List.of("cookie"));

        Response response = resource.register(new RegisterRequest("a@test.com", "secret123"));

        assertThat(response.getStatus()).isEqualTo(201);
    }

    @Test
    void forgotPasswordAlwaysReturnsOk() {
        Response response = resource.forgotPassword(new ForgotPasswordRequest("a@test.com"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(authService).forgotPassword("a@test.com");
    }

    @Test
    void changePinRequiresAdminAuth() {
        Response unauthorized = resource.changePin(contextWithAuth(null), Map.of("oldPin", "1", "newPin", "2"));
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        Response forbidden = resource.changePin(contextWithAuth(childAuth(10)), Map.of("oldPin", "1", "newPin", "2"));
        assertThat(forbidden.getStatus()).isEqualTo(401);
    }

    @Test
    void changePinUsesAuthService() {
        when(authService.changeAdminPin("fam-1", "old", "newpass"))
            .thenReturn(OperationResult.success(null));

        Response ok = resource.changePin(contextWithAuth(adminAuth()), Map.of("oldPin", "old", "newPin", "newpass"));
        assertThat(ok.getStatus()).isEqualTo(200);

        when(authService.changeAdminPin("fam-1", "old", "newpass"))
            .thenReturn(OperationResult.failure("bad"));
        Response bad = resource.changePin(contextWithAuth(adminAuth()), Map.of("oldPin", "old", "newPin", "newpass"));
        assertThat(bad.getStatus()).isEqualTo(400);
    }

    @Test
    void resetPasswordAndVerifyMapServiceResult() {
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
    void authConfigReflectsFeatureFlags() {
        Response response = resource.authConfig();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isNotNull();
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
