package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import java.util.Map;

public final class TelegramCopy {
  private TelegramCopy() {}

  private static final TelegramMessageResolver MESSAGES = new TelegramMessageResolver();
  private static String text(String key) {
    return MESSAGES.text(TelegramLocaleContext.current(), key);
  }
  public static String myTasks(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.menu.tasks"); }
  public static String rewards(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.menu.rewards"); }
  static String requests(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.menu.requests"); }
  static String coins(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.menu.coins"); }
  static String recent(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.menu.recent"); }
  static String switchChild(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.menu.switchChild"); }
  static String language(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.menu.language"); }
  static String languageEnglish(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.language.english"); }
  static String languageRussian(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.language.russian"); }
  static String languagePrompt(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.language.prompt"); }
  static String languageUpdated(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.language.updated"); }
  static String languageUnchanged(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.language.unchanged"); }
  static String languageError(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.language.error"); }
  public static String approve(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.approve"); }
  public static String reject(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.reject"); }
  static String next(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.next"); }
  static String retry(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.retry"); }
  static String cancel(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.cancel"); }
  static String site(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.site"); }
  static String shareSite(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.shareSite"); }
  static String addChild(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.addChild"); }
  static String customAmount(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.customAmount"); }
  static String fullHistory(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.fullHistory"); }
  static String allTasks(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.allTasks"); }
  static String allRewards(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.allRewards"); }
  static String confirm(FamilyLocale locale) { return MESSAGES.text(locale, "telegram.action.confirm"); }

  public static String coinAdd(int amount) {
    return TelegramBotEmoji.COINS + " +" + amount;
  }

  public static String coinRemove(int amount) {
    return TelegramBotEmoji.COINS + " -" + amount;
  }

  public static String doneTask(String taskName) {
    return MESSAGES.text(TelegramLocaleContext.current(), "telegram.request.done", Map.of("name", taskName));
  }

  public static String getReward(String rewardName) {
    return MESSAGES.text(TelegramLocaleContext.current(), "telegram.request.reward", Map.of("name", rewardName));
  }

  public static String chooseChild(String childName, int balance) {
    return TelegramBotEmoji.CHILD + " " + childName + " · " + balance;
  }

  public static String chooseChildTitle() {
    return MESSAGES.text(TelegramLocaleContext.current(), "telegram.home.selectChild");
  }

  public static String parentHome(String childName, int balance, int pending) {
    String body = TelegramBotEmoji.CHILD + " " + childName + "\n" + coinsLine(balance);
    String attention =
        pending > 0
            ? "\n\n" + MESSAGES.text(TelegramLocaleContext.current(), "telegram.home.attention", Map.of("pending", pending))
            : "\n\n" + MESSAGES.text(TelegramLocaleContext.current(), "telegram.home.noAttention");
    return body + attention;
  }

  public static String parentCoins(String childName, int balance) {
    return TelegramBotEmoji.CHILD
        + " "
        + childName
        + "\n"
        + TelegramBotEmoji.COINS
        + " " + MESSAGES.text(TelegramLocaleContext.current(), "telegram.home.balance", Map.of("balance", balance))
        + "\n\n"
        + TelegramBotEmoji.ADD + " " + text("telegram.coins.add") + "\n"
        + TelegramBotEmoji.REMOVE + " " + text("telegram.coins.remove");
  }

  public static String coinApplied(int delta, int balance) {
    String verb = delta > 0 ? text("telegram.coins.added") : text("telegram.coins.removed");
    return TelegramBotEmoji.SUCCESS
        + " "
        + verb
        + " "
        + TelegramCoinCopy.delta(delta, delta > 0, true)
        + "\n"
        + coinsLine(balance);
  }

  public static String coinConfirmText(String childName, int delta) {
    int amount = Math.abs(delta);
    String key = delta > 0 ? "telegram.coins.confirmAdd" : "telegram.coins.confirmRemove";
    return MESSAGES.text(TelegramLocaleContext.current(), key,
        Map.of("amount", amount, "coins", moneta(amount), "child", childName));
  }

  public static String childHome(String childName, int balance) {
    return MESSAGES.text(TelegramLocaleContext.current(), "telegram.home.child", Map.of("child", childName))
        + "\n" + coinsLine(balance);
  }

  public static String requestQueue(int index, int total) {
    return MESSAGES.text(TelegramLocaleContext.current(), "telegram.request.queue", Map.of("index", index, "total", total));
  }

  public static String requestQueueText(
      String childName, String title, int coins, boolean task, int index, int total) {
    return requestQueue(index, total)
        + "\n\n"
        + TelegramBotEmoji.CHILD
        + " "
        + childName
        + "\n\n"
        + title
        + "\n"
        + TelegramCoinCopy.delta(coins, task, true);
  }

  public static String requestNotification(
      String childName, String title, int coins, boolean task) {
    String key = task ? "telegram.notification.taskRequest" : "telegram.notification.rewardRequest";
    return MESSAGES.text(TelegramLocaleContext.current(), key,
        Map.of("child", childName, "title", title,
            "coins", TelegramCoinCopy.delta(coins, task, true)));
  }

  public static String coinsLine(int n) {
    return TelegramBotEmoji.COINS + " " + n + " " + moneta(n);
  }

  public static String moneta(int n) {
    int abs = Math.abs(n) % 100;
    int last = abs % 10;
    if (abs > 10 && abs < 20) {
      return text("telegram.coins.plural");
    }
    if (last == 1) {
      return text("telegram.coins.one");
    }
    if (last >= 2 && last <= 4) {
      return text("telegram.coins.few");
    }
    return text("telegram.coins.plural");
  }
}
