package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

// EXPLAIN: Resolve bot-owned presentation without putting locale decisions in domain events.
public final class TelegramMessageResolver {
  private static final String BASE_NAME = "telegram_messages";

  public String text(FamilyLocale locale, String key, Map<String, ?> parameters) {
    Locale javaLocale = locale == FamilyLocale.ru ? Locale.forLanguageTag("ru") : Locale.ENGLISH;
    ResourceBundle bundle = ResourceBundle.getBundle(BASE_NAME, javaLocale);
    String template = bundle.containsKey(key) ? bundle.getString(key) : key;
    String formatted = template;
    for (Map.Entry<String, ?> parameter : parameters.entrySet()) {
      formatted = formatted.replace("{" + parameter.getKey() + "}", String.valueOf(parameter.getValue()));
    }
    return formatted;
  }

  public String text(FamilyLocale locale, String key) {
    return text(locale, key, Map.of());
  }
}
