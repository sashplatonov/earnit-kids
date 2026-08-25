package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.request.RequestResolutionStatus;

public final class TelegramOutcomeCopy {
  private TelegramOutcomeCopy() {}

  public static String parentApproved(String title, int delta, int balance, boolean task) {
    String result = TelegramBotEmoji.SUCCESS + " Одобрено\n\n" + title;
    if (delta != 0) {
      result += "\n" + TelegramCoinCopy.delta(delta, task, true);
    }
    result += "\nБаланс: " + balance;
    return result;
  }

  public static String parentRejected() {
    return TelegramBotEmoji.REJECT + " Отклонено";
  }

  public static String stale() {
    return TelegramBotEmoji.INFO + " Этот запрос уже обработан";
  }

  public static String requestResolved(String title, RequestResolutionStatus status) {
    String statusLine =
        switch (status) {
          case approved -> TelegramBotEmoji.SUCCESS + " Одобрено";
          case rejected -> TelegramBotEmoji.DECLINE + " Отклонено";
          case cancelled -> TelegramBotEmoji.CANCEL + " Отменено";
          case deleted -> TelegramBotEmoji.DELETE + " Удалено";
        };
    return title == null || title.isBlank() ? statusLine : title + "\n" + statusLine;
  }

  public static String error() {
    return TelegramBotEmoji.ERROR + " Не удалось выполнить действие\nПопробуйте ещё раз";
  }

  public static String waiting(String taskName) {
    return TelegramBotEmoji.WAITING + " " + taskName + "\nЖдём решения родителя";
  }

  public static String rewardWaiting() {
    return TelegramBotEmoji.WAITING + " Заявка отправлена родителю";
  }

  public static String childTaskApproved(String title, int delta, int balance) {
    return TelegramBotEmoji.CELEBRATE
        + " "
        + title
        + " одобрен\n\n"
        + TelegramCoinCopy.delta(delta, true, true)
        + "\nБаланс: "
        + balance;
  }

  public static String childRewardApproved(String title) {
    return TelegramBotEmoji.CELEBRATE + " Награда одобрена\n\n" + title;
  }

  public static String childTaskRejected(String title) {
    return TelegramBotEmoji.DECLINE + " " + title + " не одобрен";
  }

  public static String childRewardRejected(String title) {
    return TelegramBotEmoji.DECLINE + " Награда не одобрена\n\n" + title;
  }

  public static String emptyRequests() {
    return TelegramBotEmoji.SUCCESS + " Нет запросов, ожидающих решения";
  }

  public static String emptyTasks() {
    return TelegramBotEmoji.SUCCESS + " На сегодня активных заданий нет";
  }

  public static String emptyRewards() {
    return TelegramBotEmoji.REWARDS + " Сейчас нет доступных наград";
  }

  public static String emptyRecent() {
    return TelegramBotEmoji.SUCCESS + " Пока нет событий";
  }
}
