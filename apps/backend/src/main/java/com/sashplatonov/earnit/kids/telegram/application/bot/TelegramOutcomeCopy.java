package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.request.RequestResolutionStatus;
import java.util.Map;

public final class TelegramOutcomeCopy {
  private TelegramOutcomeCopy() {}

  public static String parentApproved(String title, int delta, int balance, boolean task) {
    String result = TelegramMessageResolverHolder.text("telegram.outcome.approved") + "\n\n" + title;
    if (delta != 0) {
      result += "\n" + TelegramCoinCopy.delta(delta, task, true);
    }
    result += "\n" + TelegramMessageResolverHolder.text("telegram.notification.balance", Map.of("balance", balance));
    return result;
  }

  public static String parentRejected() {
    return TelegramMessageResolverHolder.text("telegram.outcome.rejected");
  }

  public static String stale() {
    return TelegramMessageResolverHolder.text("telegram.outcome.stale");
  }

  public static String requestResolved(String title, RequestResolutionStatus status) {
    String statusLine =
        switch (status) {
          case approved -> TelegramMessageResolverHolder.text("telegram.outcome.approved");
          case rejected -> TelegramMessageResolverHolder.text("telegram.outcome.rejected");
          case cancelled -> TelegramMessageResolverHolder.text("telegram.outcome.cancelled");
          case deleted -> TelegramMessageResolverHolder.text("telegram.outcome.deleted");
        };
    return title == null || title.isBlank() ? statusLine : title + "\n" + statusLine;
  }

  public static String error() {
    return TelegramMessageResolverHolder.text("telegram.outcome.error");
  }

  public static String waiting(String taskName) {
    return TelegramBotEmoji.WAITING + " " + taskName + "\n" + TelegramMessageResolverHolder.text("telegram.outcome.waiting");
  }

  public static String rewardWaiting() {
    return TelegramMessageResolverHolder.text("telegram.outcome.sent");
  }

  public static String childTaskApproved(String title, int delta, int balance) {
    return TelegramMessageResolverHolder.text("telegram.outcome.childTaskApproved", Map.of(
        "title", title, "coins", TelegramCoinCopy.delta(delta, true, true), "balance", balance));
  }

  public static String childRewardApproved(String title) {
    return TelegramMessageResolverHolder.text("telegram.outcome.childRewardApproved", Map.of("title", title));
  }

  public static String childTaskRejected(String title) {
    return TelegramMessageResolverHolder.text("telegram.outcome.childTaskRejected", Map.of("title", title));
  }

  public static String childRewardRejected(String title) {
    return TelegramMessageResolverHolder.text("telegram.outcome.childRewardRejected", Map.of("title", title));
  }

  public static String emptyRequests() {
    return TelegramMessageResolverHolder.text("telegram.outcome.noRequests");
  }

  public static String emptyTasks() {
    return TelegramMessageResolverHolder.text("telegram.outcome.noTasks");
  }

  public static String emptyRewards() {
    return TelegramMessageResolverHolder.text("telegram.outcome.noRewards");
  }

  public static String emptyRecent() {
    return TelegramMessageResolverHolder.text("telegram.outcome.noRecent");
  }
}
