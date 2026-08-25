package com.sashplatonov.earnit.kids.telegram.application.bot;

import java.util.Map;

final class TelegramMessageResolverHolder {
  private static final TelegramMessageResolver RESOLVER = new TelegramMessageResolver();
  private TelegramMessageResolverHolder() {}
  static String text(String key) { return RESOLVER.text(TelegramLocaleContext.current(), key); }
  static String text(String key, Map<String, ?> parameters) {
    return RESOLVER.text(TelegramLocaleContext.current(), key, parameters);
  }
}
