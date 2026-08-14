package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramWebhookRegistrationServiceTest {
    @Test
    void registersPublicWebhookWhenBotIsEnabled() throws Exception {
        TelegramFeatureGate featureGate = mock(TelegramFeatureGate.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        when(featureGate.isBotEnabled()).thenReturn(true);
        when(config.miniAppUrl()).thenReturn(Optional.of("https://earnit.example/telegram"));
        when(config.webhookSecret()).thenReturn(Optional.of("secret"));
        var service = new TelegramWebhookRegistrationService(featureGate, config, apiClient);

        service.register();

        verify(apiClient).registerWebhook(URI.create("https://earnit.example/api/telegram/webhook"), "secret");
    }

    @Test
    void skipsRegistrationWhenBotIsDisabled() throws Exception {
        TelegramFeatureGate featureGate = mock(TelegramFeatureGate.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        when(featureGate.isBotEnabled()).thenReturn(false);
        var service = new TelegramWebhookRegistrationService(featureGate, config, apiClient);

        service.register();

        verify(apiClient, never()).registerWebhook(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsNonHttpsMiniAppUrl() {
        assertThatThrownBy(() -> TelegramWebhookRegistrationService.webhookUrl("http://earnit.example/telegram"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Telegram Mini App URL must be a public HTTPS URL");
    }

    @Test
    void derivesWebhookFromMiniAppOrigin() {
        assertThat(TelegramWebhookRegistrationService.webhookUrl("https://earnit.example/telegram"))
            .isEqualTo(URI.create("https://earnit.example/api/telegram/webhook"));
    }
}
