package com.sashplatonov.earnit.kids.resource.auth;

import com.sashplatonov.earnit.kids.config.auth.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.service.auth.AuthService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChildMagicLinkResourceTest {

    @Mock AuthService authService;
    @Mock CookieBuilder cookieBuilder;
    @Mock jakarta.ws.rs.container.ContainerRequestContext request;

    private ChildMagicLinkResource resource;

    @BeforeEach
    void setUp() {
        resource = new ChildMagicLinkResource(authService, cookieBuilder);
    }

    @Test
    void loginByToken_validToken_redirectsToRootAndSetsCookies() {
        AuthPayload payload = new AuthPayload("fam-1", "c@test.com", "child", 10, "Kid", false, "child", null, false);
        when(authService.authenticateChild("token")).thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("c@test.com", "child", "fam-1", 10, "child"))
            .thenReturn(List.of("cookie-1", "cookie-2"));

        Response response = resource.loginByToken(request, "token");

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getLocation().toString()).isEqualTo("/");
        assertThat(response.getHeaders().get("Set-Cookie")).hasSize(2);
    }

    @Test
    void loginByToken_invalidToken_redirectsToLogin() {
        when(authService.authenticateChild("bad")).thenReturn(OperationResult.failure("bad"));

        Response response = resource.loginByToken(request, "bad");

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getLocation().toString()).isEqualTo("/login.html?error=invalid_token");
    }
}
