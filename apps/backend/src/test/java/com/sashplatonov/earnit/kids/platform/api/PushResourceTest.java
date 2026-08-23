package com.sashplatonov.earnit.kids.platform.api;

import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.platform.webpush.WebPushService;
import com.sashplatonov.earnit.kids.platform.webpush.WebPushSubscriptionRequest;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Optional;

class PushResourceTest {

    private PushResource resource;

    @BeforeEach
    void setUp() {
        resource = new PushResource();
    }

    @Test
    void register_missingOrPresentAuth_returnsExpectedStatus() {
        Response unauthorized = resource.register(contextWithAuth(null));
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        Response ok = resource.register(contextWithAuth(new AuthContext("fam-1", 10, "child", "c@test.com", "csrf", false, "child")));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void unregister_missingOrPresentAuth_returnsExpectedStatus() {
        Response unauthorized = resource.unregister(contextWithAuth(null));
        assertThat(unauthorized.getStatus()).isEqualTo(401);

        Response ok = resource.unregister(contextWithAuth(new AuthContext("fam-1", null, "admin", "a@test.com", "csrf", false, "family_admin")));
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    void invalidSubscriptionReturnsBadRequest() {
        WebPushService service = mock(WebPushService.class);
        doThrow(new IllegalArgumentException("invalid"))
            .when(service).register(any(), any());
        PushResource resourceWithService = new PushResource(service);

        Response response = resourceWithService.register(
            new WebPushSubscriptionRequest("http://invalid", "key", "auth"),
            contextWithAuth(new AuthContext("fam-1", null, "admin", "a@test.com", "csrf", false,
                "family_admin", 10)));

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void publicVapidKeyRequiresAuthAndNeverReturnsPrivateMaterial() {
        WebPushService service = mock(WebPushService.class);
        when(service.publicVapidKey()).thenReturn(Optional.of("public-key"));
        PushResource resourceWithService = new PushResource(service);

        assertThat(resourceWithService.vapidPublicKey(contextWithAuth(null)).getStatus()).isEqualTo(401);
        Response response = resourceWithService.vapidPublicKey(contextWithAuth(
            new AuthContext("fam-1", null, "admin", "a@test.com", "csrf", false, "family_admin", 10)));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(new WebPushPublicKeyResponse("public-key"));
        assertThat(response.getHeaderString("Cache-Control")).isEqualTo("no-store");
    }

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }
}
