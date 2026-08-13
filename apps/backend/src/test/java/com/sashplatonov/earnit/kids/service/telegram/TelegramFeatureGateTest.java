package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramFeatureGateTest {
    @Test
    void gateIsClosedByDefaultAndRequiresConfiguredEntryPoint() {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.enabled()).thenReturn(false);
        when(config.botToken()).thenReturn(Optional.of("token"));
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));

        assertThat(new TelegramFeatureGate(config).isEnabled()).isFalse();
    }

    @Test
    void botGateRequiresMiniAppEntryPoint() {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.botEnabled()).thenReturn(true);
        when(config.botToken()).thenReturn(Optional.of("token"));
        when(config.webhookSecret()).thenReturn(Optional.of("secret"));
        when(config.miniAppUrl()).thenReturn(Optional.empty());

        assertThat(new TelegramFeatureGate(config).isBotEnabled()).isFalse();
    }

    @Test
    void stagedRolloutAllowsOnlyConfiguredFamily() {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.enabled()).thenReturn(true);
        when(config.miniAppEnabled()).thenReturn(false);
        when(config.botToken()).thenReturn(Optional.of("token"));
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        when(config.rolloutFamilyId()).thenReturn(Optional.of("family-test"));

        TelegramFeatureGate gate = new TelegramFeatureGate(config);
        assertThat(gate.isEnabled()).isTrue();
        assertThat(gate.isMiniAppEnabled("family-test")).isTrue();
        assertThat(gate.isMiniAppEnabled("other-family")).isFalse();
    }

    @Test
    void botRolloutAllowsOnlyConfiguredFamily() {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.botEnabled()).thenReturn(true);
        when(config.botToken()).thenReturn(Optional.of("token"));
        when(config.webhookSecret()).thenReturn(Optional.of("secret"));
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        when(config.rolloutFamilyId()).thenReturn(Optional.of("family-test"));

        TelegramFeatureGate gate = new TelegramFeatureGate(config);

        assertThat(gate.isBotEnabled("family-test")).isTrue();
        assertThat(gate.isBotEnabled("other-family")).isFalse();
    }
}
