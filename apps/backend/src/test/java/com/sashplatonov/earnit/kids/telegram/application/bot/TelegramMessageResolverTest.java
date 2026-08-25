package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMessageResolverTest {
  @Test
  void resolvesTheSameKeyForBothFamilyLocales() {
    TelegramMessageResolver resolver = new TelegramMessageResolver();

    assertThat(resolver.text(FamilyLocale.en, "telegram.request.queue",
        Map.of("index", 2, "total", 4))).isEqualTo("Request 2 of 4");
    assertThat(resolver.text(FamilyLocale.ru, "telegram.request.queue",
        Map.of("index", 2, "total", 4))).isEqualTo("🎯 Запрос 2 из 4");
  }
}
