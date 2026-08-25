package com.sashplatonov.earnit.kids.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TelegramCatalogContractTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)}");

    @Test
    void englishAndRussianTelegramCatalogsHaveMatchingKeysAndPlaceholders() {
        ResourceBundle english = ResourceBundle.getBundle("telegram_messages", Locale.ENGLISH);
        ResourceBundle russian = ResourceBundle.getBundle("telegram_messages", Locale.forLanguageTag("ru"));

        assertThat(russian.keySet()).containsExactlyInAnyOrderElementsOf(english.keySet());
        for (String key : english.keySet()) {
            assertThat(placeholders(english.getString(key))).as("placeholder contract for %s", key)
                .containsExactlyInAnyOrderElementsOf(placeholders(russian.getString(key)));
        }
    }

    private Set<String> placeholders(String template) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        Set<String> placeholders = new java.util.HashSet<>();
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
    }
}
