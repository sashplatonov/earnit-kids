package com.sashplatonov.earnit.kids.telegram.api.resource;

import com.sashplatonov.earnit.kids.config.auth.CookieBuilder;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.telegram.application.auth.TelegramMiniAppAuthService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramMiniAppAuthResourceTest {
    @Test
    void exchangeDoesNotExposeFunctionalAuthWhenGateIsClosed() {
        TelegramFeatureGate gate = mock(TelegramFeatureGate.class);
        when(gate.isEnabled()).thenReturn(false);
        TelegramMiniAppAuthResource resource = new TelegramMiniAppAuthResource(
            gate, mock(TelegramMiniAppAuthService.class), mock(CookieBuilder.class));

        try (Response response = resource.exchange(new TelegramMiniAppAuthResource.TelegramInitDataRequest("init-data", null))) {
            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    @Test
    void exchangeCreatesTheExistingScopedSessionOnlyAfterSuccessfulVerification() {
        TelegramFeatureGate gate = mock(TelegramFeatureGate.class);
        TelegramMiniAppAuthService authService = mock(TelegramMiniAppAuthService.class);
        CookieBuilder cookies = mock(CookieBuilder.class);
        when(gate.isEnabled()).thenReturn(true);
        when(authService.authenticate("valid-data", null)).thenReturn(OperationResult.success(
            new AuthPayload("family-1", "parent@example.test", "admin", null, null, false, "family_admin", null, false)));
        when(cookies.buildAuthCookies("parent@example.test", "admin", "family-1", null, "family_admin"))
            .thenReturn(List.of("app_auth=signed; Path=/"));
        TelegramMiniAppAuthResource resource = new TelegramMiniAppAuthResource(gate, authService, cookies);

        try (Response response = resource.exchange(new TelegramMiniAppAuthResource.TelegramInitDataRequest("valid-data", null))) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getStringHeaders().getFirst("Set-Cookie")).startsWith("app_auth=signed");
        }
    }
}
