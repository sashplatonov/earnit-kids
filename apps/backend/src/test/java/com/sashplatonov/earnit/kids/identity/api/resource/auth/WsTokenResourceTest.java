package com.sashplatonov.earnit.kids.identity.api.resource.auth;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.config.auth.JwtService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WsTokenResourceTest {
    private final JwtService jwt = mock(JwtService.class);
    private final WsTokenResource resource = new WsTokenResource(jwt);
    private final ContainerRequestContext context = mock(ContainerRequestContext.class);

    @Test
    void rejectsAnonymousRequest() {
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(null);
        try (Response response = resource.getWebSocketToken(context)) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
        verifyNoInteractions(jwt);
    }

    @Test
    void signsParentAndChildPayloads() {
        when(jwt.signToken(anyMap(), eq(60))).thenReturn("signed");
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY))
            .thenReturn(new AuthContext("family-1", 3, "child", "child@test", "csrf", false, "child"));
        try (Response response = resource.getWebSocketToken(context)) {
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(jwt).signToken(eq(Map.of("familyId", "family-1", "childId", 3, "role", "child")), eq(60L));
    }

    @Test
    void omitsChildIdForParentPayload() {
        when(jwt.signToken(anyMap(), eq(60L))).thenReturn("signed");
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY))
            .thenReturn(new AuthContext("family-1", null, "admin", "parent@test", "csrf", false, "family_admin"));

        try (Response response = resource.getWebSocketToken(context)) {
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(jwt).signToken(eq(Map.of("familyId", "family-1", "role", "admin")), eq(60L));
    }
}
