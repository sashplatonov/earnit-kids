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

  @Test
  void hidesUnknownKeysWithLocalizedFallback() {
    TelegramMessageResolver resolver = new TelegramMessageResolver();

    assertThat(resolver.text(FamilyLocale.en, "telegram.missing")).isEqualTo(
        "⚠️ This message is temporarily unavailable");
    assertThat(resolver.text(FamilyLocale.ru, "telegram.missing")).isEqualTo(
        "⚠️ Это сообщение временно недоступно");
  }

  @Test
  void resolvesInteractiveAndOutcomeCopyForTheRecipientFamily() throws Exception {
    String[] english = new String[1];
    TelegramLocaleContext.with(FamilyLocale.en, () -> english[0] =
        TelegramCopy.requestNotification("Alex", "Homework", 2, true)
            + "\n" + TelegramCopy.approve(FamilyLocale.en)
            + "\n" + TelegramOutcomeCopy.childTaskApproved("Homework", 2, 12));
    String[] russian = new String[1];
    TelegramLocaleContext.with(FamilyLocale.ru, () -> russian[0] =
        TelegramCopy.requestNotification("Alex", "Домашнее задание", 2, true)
            + "\n" + TelegramCopy.approve(FamilyLocale.ru)
            + "\n" + TelegramOutcomeCopy.childTaskApproved("Домашнее задание", 2, 12));

    assertThat(english[0]).contains("completed", "Approve", "approved").doesNotContain("Одобрить", "одобрен");
    assertThat(russian[0]).contains("выполнила", "Одобрить", "одобрен").doesNotContain("completed", "Approve");
  }
}
