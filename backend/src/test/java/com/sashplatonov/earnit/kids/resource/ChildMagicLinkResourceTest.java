package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.service.AuthService;
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

    private ChildMagicLinkResource resource;

    @BeforeEach
    void setUp() {
        resource = new ChildMagicLinkResource(authService, cookieBuilder);
    }

    @Test
    void redirectsToRootAndSetsCookiesOnSuccess() {
        AuthPayload payload = new AuthPayload("fam-1", "c@test.com", "child", 10, "Kid");
        when(authService.authenticateChild("token")).thenReturn(OperationResult.success(payload));
        when(cookieBuilder.buildAuthCookies("c@test.com", "child", "fam-1", 10, 604800))
            .thenReturn(List.of("cookie-1", "cookie-2"));

        Response response = resource.loginByToken("token");

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getLocation().toString()).isEqualTo("/");
        assertThat(response.getHeaders().get("Set-Cookie")).hasSize(2);
    }

    @Test
    void redirectsToLoginOnFailure() {
        when(authService.authenticateChild("bad")).thenReturn(OperationResult.failure("bad"));

        Response response = resource.loginByToken("bad");

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getLocation().toString()).isEqualTo("/login.html?error=invalid_token");
    }
}
