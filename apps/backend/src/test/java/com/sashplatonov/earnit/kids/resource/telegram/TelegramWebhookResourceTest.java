package com.sashplatonov.earnit.kids.resource.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.service.telegram.TelegramBotService;
import com.sashplatonov.earnit.kids.service.telegram.TelegramFeatureGate;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramWebhookResourceTest {
    @Test
    void rejectsInvalidSecretBeforeDelegating() throws Exception {
        TelegramFeatureGate gate = mock(TelegramFeatureGate.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramBotService service = mock(TelegramBotService.class);
        when(gate.isBotEnabled()).thenReturn(true);
        when(config.webhookSecret()).thenReturn(Optional.of("expected"));
        TelegramWebhookResource resource = new TelegramWebhookResource(gate, config, service);

        try (Response response = resource.receive("wrong", new ObjectMapper().readTree("{\"update_id\":1}"))) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
        verify(service, never()).handleUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delegatesValidSecretAndReturnsOk() throws Exception {
        TelegramFeatureGate gate = mock(TelegramFeatureGate.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramBotService service = mock(TelegramBotService.class);
        when(gate.isBotEnabled()).thenReturn(true);
        when(config.webhookSecret()).thenReturn(Optional.of("expected"));
        TelegramWebhookResource resource = new TelegramWebhookResource(gate, config, service);
        var update = new ObjectMapper().readTree("{\"update_id\":1}");

        try (Response response = resource.receive("expected", update)) {
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(service).handleUpdate(update);
    }
}
