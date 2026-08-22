package com.sashplatonov.earnit.kids.telegram.api.resource;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.telegram.api.request.TelegramLinkCompletionRequest;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramAccountConnectionService;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelegramAccountConnectionResourceTest {
    @Test
    void startRequiresAnAuthenticatedParent() {
        TelegramAccountConnectionService connections = mock(TelegramAccountConnectionService.class);
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        TelegramAccountConnectionResource resource = new TelegramAccountConnectionResource(
            connections, mock(TelegramFeatureGate.class));

        try (Response response = resource.start(context)) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
        verifyNoInteractions(connections);
    }

    @Test
    void startHonorsTheSelectedFamilysRolloutGate() {
        TelegramAccountConnectionService connections = mock(TelegramAccountConnectionService.class);
        TelegramFeatureGate gate = mock(TelegramFeatureGate.class);
        ContainerRequestContext context = parentContext();
        when(gate.isMiniAppEnabled("family-1")).thenReturn(false);
        TelegramAccountConnectionResource resource = new TelegramAccountConnectionResource(connections, gate);

        try (Response response = resource.start(context)) {
            assertThat(response.getStatus()).isEqualTo(404);
        }
        verifyNoInteractions(connections);
    }

    @Test
    void startUsesTheAuthenticatedParentsSelectedFamily() {
        TelegramAccountConnectionService connections = mock(TelegramAccountConnectionService.class);
        TelegramFeatureGate gate = mock(TelegramFeatureGate.class);
        ContainerRequestContext context = parentContext();
        when(gate.isMiniAppEnabled("family-1")).thenReturn(true);
        when(connections.start("family-1", "parent@example.test")).thenReturn(OperationResult.success(
            new TelegramLinkLaunchResponse("https://t.me/earnit_bot?startapp=opaque")));
        TelegramAccountConnectionResource resource = new TelegramAccountConnectionResource(connections, gate);

        try (Response response = resource.start(context)) {
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void completionStaysAvailableWithoutABrowserSessionOnlyWhileTelegramIsEnabled() {
        TelegramAccountConnectionService connections = mock(TelegramAccountConnectionService.class);
        TelegramFeatureGate gate = mock(TelegramFeatureGate.class);
        when(gate.isEnabled()).thenReturn(false);
        TelegramAccountConnectionResource resource = new TelegramAccountConnectionResource(connections, gate);

        try (Response response = resource.complete(new TelegramLinkCompletionRequest("token", "init-data"))) {
            assertThat(response.getStatus()).isEqualTo(404);
        }
        verifyNoInteractions(connections);
    }

    private ContainerRequestContext parentContext() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(new AuthContext(
            "family-1", null, "admin", "parent@example.test", "csrf", false, "editor"));
        return context;
    }
}
