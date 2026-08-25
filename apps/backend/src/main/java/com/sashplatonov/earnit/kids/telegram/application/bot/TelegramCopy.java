package com.sashplatonov.earnit.kids.telegram.application.bot;

public final class TelegramCopy {
  private TelegramCopy() {}

  public static final String MY_TASKS = TelegramBotEmoji.TASKS + " Мои задания";
  public static final String REWARDS = TelegramBotEmoji.REWARDS + " Награды";
  public static final String REQUESTS = TelegramBotEmoji.REQUESTS + " Запросы";
  public static final String COINS = TelegramBotEmoji.COINS + " Монеты";
  public static final String RECENT = TelegramBotEmoji.RECENT + " Последние";
  public static final String SWITCH_CHILD = TelegramBotEmoji.CHILD + " Выбрать ребёнка";
  public static final String ADD_CHILD_MINI_APP =
      TelegramBotEmoji.ADD + " Добавить ребёнка → Mini App";
  public static final String APPROVE = TelegramBotEmoji.APPROVE + " Одобрить";
  public static final String REJECT = TelegramBotEmoji.REJECT + " Отклонить";
  public static final String NEXT = TelegramBotEmoji.NEXT + " Следующий";
  public static final String CUSTOM_AMOUNT = TelegramBotEmoji.CUSTOM + " Другая сумма";
  public static final String FULL_HISTORY = TelegramBotEmoji.MINI_APP + " Полная история";
  public static final String ALL_TASKS = TelegramBotEmoji.MINI_APP + " Все задания";
  public static final String ALL_REWARDS = TelegramBotEmoji.MINI_APP + " Все награды";
  public static final String RETRY = TelegramBotEmoji.REFRESH + " Повторить";
  public static final String CONFIRM = TelegramBotEmoji.APPROVE + " Подтвердить";
  public static final String CANCEL = TelegramBotEmoji.REJECT + " Отмена";
  public static final String SHARE_SITE = TelegramBotEmoji.LINK + " Сайт";

  public static final String NAV_REQUESTS = TelegramBotEmoji.REQUESTS + " Запросы";
  public static final String NAV_COINS = TelegramBotEmoji.COINS + " Монеты";
  public static final String NAV_RECENT = TelegramBotEmoji.RECENT + " Последние";
  public static final String NAV_SELECT_CHILD = TelegramBotEmoji.CHILD + " Выбрать ребёнка";
  public static final String NAV_OPEN_SITE = TelegramBotEmoji.SITE + " Сайт";

  public static String coinAdd(int amount) {
    return TelegramBotEmoji.COINS + " +" + amount;
  }

  public static String coinRemove(int amount) {
    return TelegramBotEmoji.COINS + " -" + amount;
  }

  public static String doneTask(String taskName) {
    return TelegramBotEmoji.TASKS + " Готово: " + taskName;
  }

  public static String getReward(String rewardName) {
    return TelegramBotEmoji.REWARDS + " Получить: " + rewardName;
  }

  public static String chooseChild(String childName, int balance) {
    return TelegramBotEmoji.CHILD + " " + childName + " · " + balance;
  }

  public static String chooseChildTitle() {
    return TelegramBotEmoji.CHILD + " Кого показывать?";
  }

  public static String parentHome(String childName, int balance, int pending) {
    String body = TelegramBotEmoji.CHILD + " " + childName + "\n" + coinsLine(balance);
    String attention =
        pending > 0
            ? "\n\n" + TelegramBotEmoji.REQUESTS + " Требуют внимания: " + pending
            : "\n\n" + TelegramBotEmoji.SUCCESS + " Сейчас ничего не требует внимания";
    return body + attention;
  }

  public static String parentCoins(String childName, int balance) {
    return TelegramBotEmoji.CHILD
        + " "
        + childName
        + "\n"
        + TelegramBotEmoji.COINS
        + " Баланс: "
        + balance
        + "\n\n"
        + TelegramBotEmoji.ADD
        + " Добавить монеты\n"
        + TelegramBotEmoji.REMOVE
        + " Снять монеты";
  }

  public static String coinApplied(int delta, int balance) {
    String verb = delta > 0 ? "Добавлено" : "Снято";
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
    String verb = delta > 0 ? "Добавить" : "Снять";
    String preposition = delta > 0 ? " для " : " с ";
    return verb + " " + amount + " " + moneta(amount) + preposition + childName + "?";
  }

  public static String childHome(String childName, int balance) {
    return TelegramBotEmoji.GREETING + " " + childName + "\n" + coinsLine(balance);
  }

  public static String requestQueue(int index, int total) {
    return TelegramBotEmoji.REQUESTS + " Запрос " + index + " из " + total;
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
    String lead = task ? " выполнила:" : " хочет награду:";
    return TelegramBotEmoji.CHILD
        + " "
        + childName
        + lead
        + "\n\n"
        + title
        + "\n"
        + TelegramCoinCopy.delta(coins, task, true);
  }

  public static String coinsLine(int n) {
    return TelegramBotEmoji.COINS + " " + n + " " + moneta(n);
  }

  public static String moneta(int n) {
    int abs = Math.abs(n) % 100;
    int last = abs % 10;
    if (abs > 10 && abs < 20) {
      return "монет";
    }
    if (last == 1) {
      return "монета";
    }
    if (last >= 2 && last <= 4) {
      return "монеты";
    }
    return "монет";
  }
}
