package com.sashplatonov.earnit.kids.telegram.api.resource;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramChildConnectionService;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramChildConnectionResourceTest {

    @Test
    void connection_requiresAdmin() {
        TelegramChildConnectionService service = mock(TelegramChildConnectionService.class);
        TelegramChildConnectionResource resource = new TelegramChildConnectionResource(service, mock(TelegramFeatureGate.class));
        try (Response response = resource.connection(mock(ContainerRequestContext.class), 10)) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void invite_honorsMiniAppGate() {
        TelegramChildConnectionService service = mock(TelegramChildConnectionService.class);
        TelegramFeatureGate gate = mock(TelegramFeatureGate.class);
        when(gate.isMiniAppEnabled("family-1")).thenReturn(false);
        TelegramChildConnectionResource resource = new TelegramChildConnectionResource(service, gate);
        try (Response response = resource.invite(context(), 10)) {
            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    @Test
    void connection_inviteAndUnlink_delegateWithFamilyAndChild() {
        TelegramChildConnectionService service = mock(TelegramChildConnectionService.class);
        TelegramFeatureGate gate = mock(TelegramFeatureGate.class);
        when(gate.isMiniAppEnabled("family-1")).thenReturn(true);
        when(service.connection("family-1", 10)).thenReturn(OperationResult.success(null));
        when(service.invite("family-1", 10)).thenReturn(OperationResult.success(null));
        when(service.unlink("family-1", 10)).thenReturn(OperationResult.success(null));
        TelegramChildConnectionResource resource = new TelegramChildConnectionResource(service, gate);

        try (Response connection = resource.connection(context(), 10);
             Response invite = resource.invite(context(), 10);
             Response unlink = resource.unlink(context(), 10)) {
            assertThat(connection.getStatus()).isEqualTo(200);
            assertThat(invite.getStatus()).isEqualTo(200);
            assertThat(unlink.getStatus()).isEqualTo(200);
        }
        verify(service).connection("family-1", 10);
        verify(service).invite("family-1", 10);
        verify(service).unlink("family-1", 10);
    }

    private static ContainerRequestContext context() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(
            new AuthContext("family-1", null, "admin", "parent@test", "csrf", false, "editor"));
        return context;
    }
}
