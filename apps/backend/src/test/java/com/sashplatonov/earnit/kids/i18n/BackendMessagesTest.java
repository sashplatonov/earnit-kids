package com.sashplatonov.earnit.kids.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BackendMessagesTest {

    @AfterEach
    void tearDown() {
        RequestLocaleHolder.clear();
    }

    @Test
    void message_usesRussianBundleForCurrentLocale() {
        RequestLocaleHolder.set("ru");

        assertThat(BackendMessages.message("auth.invalidPassword")).isEqualTo("Неверный пароль");
    }

    @Test
    void message_interpolatesNamedVariablesFromResourceBundles() {
        RequestLocaleHolder.set("en");

        assertThat(BackendMessages.message("family.invalidTheme", Map.of("theme", "night")))
            .isEqualTo("Invalid theme: night");
    }

    @Test
    void resolveLocale_prefersExplicitAppLocaleHeader() {
        assertThat(BackendMessages.resolveLocale("ru", "en-US,en;q=0.9")).isEqualTo("ru");
    }
}