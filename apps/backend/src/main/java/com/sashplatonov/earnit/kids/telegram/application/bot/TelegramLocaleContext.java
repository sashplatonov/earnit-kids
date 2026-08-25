package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;

public final class TelegramLocaleContext {
  private static final ThreadLocal<FamilyLocale> CURRENT =
      ThreadLocal.withInitial(() -> FamilyLocale.ru);

  private TelegramLocaleContext() {}

  public static FamilyLocale current() { return CURRENT.get(); }

  public static void with(FamilyLocale locale, ThrowingAction action) throws Exception {
    FamilyLocale previous = CURRENT.get();
    CURRENT.set(locale == null ? FamilyLocale.en : locale);
    try { action.run(); } finally { CURRENT.set(previous); }
  }

  @FunctionalInterface
  public interface ThrowingAction {
    void run() throws Exception;
  }
}
