package com.sashplatonov.earnit.kids.service.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramDeepLinkTest {
    @Test
    void contextAppendsQueryForPlainMiniAppUrl() {
        assertThat(TelegramDeepLink.coins("https://example.test/telegram"))
            .isEqualTo("https://example.test/telegram?context=coins");
        assertThat(TelegramDeepLink.history("https://example.test/telegram"))
            .isEqualTo("https://example.test/telegram?context=history");
        assertThat(TelegramDeepLink.tasks("https://example.test/telegram"))
            .isEqualTo("https://example.test/telegram?context=tasks");
        assertThat(TelegramDeepLink.rewards("https://example.test/telegram"))
            .isEqualTo("https://example.test/telegram?context=rewards");
    }

    @Test
    void contextUsesAmpersandWhenUrlAlreadyHasQuery() {
        assertThat(TelegramDeepLink.history("https://example.test/telegram?theme=dark"))
            .isEqualTo("https://example.test/telegram?theme=dark&context=history");
    }

    @Test
    void homeKeepsThePlainUrl() {
        assertThat(TelegramDeepLink.home("https://example.test/telegram"))
            .isEqualTo("https://example.test/telegram");
        assertThat(TelegramDeepLink.coins("")).isEqualTo("");
    }
}
