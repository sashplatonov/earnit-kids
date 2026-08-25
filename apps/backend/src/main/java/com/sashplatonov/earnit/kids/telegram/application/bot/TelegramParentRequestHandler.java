package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.family.api.response.RequestDto;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;
import java.util.List;

final class TelegramParentRequestHandler {
  private TelegramParentRequestHandler() {}

  static void handle(
      long telegramUserId,
      String data,
      JsonNode callback,
      TelegramQuickActionService quickActions,
      TelegramBotApiClient apiClient,
      TelegramMenuBuilder menuBuilder)
      throws Exception {
    String[] parts = data.split("\\.", -1);
    if (parts.length != 5 && parts.length != 6) {
      return;
    }
    if (!"approve".equals(parts[2]) && !"reject".equals(parts[2])) {
      return;
    }
    boolean queueContext = parts.length == 6 && "queue".equals(parts[5]);
    int childId = Integer.parseInt(parts[3]);
    long requestId = Long.parseLong(parts[4]);
    boolean approved = "approve".equals(parts[2]);
    OperationResult<TelegramQuickActionResponse> result =
        approved
            ? quickActions.approveRequest(telegramUserId, childId, requestId)
            : quickActions.rejectRequest(telegramUserId, childId, requestId);
    TelegramQuickActionResponse localizedView = result instanceof OperationResult.Success<TelegramQuickActionResponse> success
        ? success.value() : quickActions.load(telegramUserId, childId).orElse(null);
    try {
      TelegramLocaleContext.with(localizedView == null
          ? com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.en : localizedView.locale(), () ->
          editResult(callback, result,
              new TelegramParentRequestDetails(approved, requestId, childId, telegramUserId, queueContext, data),
              new TelegramParentRequestDependencies(quickActions, apiClient, menuBuilder)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void editResult(
      JsonNode callback,
      OperationResult<TelegramQuickActionResponse> result,
      TelegramParentRequestDetails details,
      TelegramParentRequestDependencies dependencies)
      throws Exception {
    long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
    long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
    if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
      return;
    }
    if (result instanceof OperationResult.Success<TelegramQuickActionResponse> success) {
      if (details.queueContext()) {
        editQueueAdvance(callback, success.value(), dependencies.apiClient(), dependencies.menuBuilder());
      } else {
        dependencies.apiClient().editMessageText(
            chatId, messageId, decisionText(success.value(), details.approved(), details.requestId()), List.of());
      }
      return;
    }
    TelegramQuickActionResponse view =
        dependencies.quickActions().load(details.telegramUserId(), details.childId()).orElse(null);
    if (isStale(view, details.requestId())) {
      dependencies.apiClient().editMessageText(chatId, messageId, TelegramOutcomeCopy.stale(), List.of());
      return;
    }
    dependencies.apiClient().editMessageText(
        chatId,
        messageId,
        TelegramOutcomeCopy.error(),
        view == null
            ? dependencies.menuBuilder().backToMain()
            : dependencies.menuBuilder().parentRequestRetry(view, details.retryData()));
  }

  private static void editQueueAdvance(
      JsonNode callback,
      TelegramQuickActionResponse view,
      TelegramBotApiClient apiClient,
      TelegramMenuBuilder menuBuilder)
      throws Exception {
    long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
    long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
    if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
      return;
    }
    List<TelegramBotApiClient.InlineButton> queue = menuBuilder.parentRequestQueue(view, null);
    if (!queue.isEmpty()) {
      apiClient.editMessageText(chatId, messageId, queueTextFor(view), queue);
      return;
    }
    apiClient.editMessageText(chatId, messageId, TelegramOutcomeCopy.emptyRequests(), List.of());
  }

  private static String queueTextFor(TelegramQuickActionResponse view) {
    List<RequestDto> pending = TelegramViewSupport.pendingRequests(view);
    if (pending.isEmpty()) {
      return TelegramOutcomeCopy.emptyRequests();
    }
    RequestDto request = pending.get(0);
    return TelegramCopy.requestQueueText(
        view.childName(),
        TelegramViewSupport.requestTitle(request, view.locale()),
        request.coins(),
        !request.requestType().isPurchase(),
        1,
        pending.size());
  }

  private static boolean isStale(TelegramQuickActionResponse view, long requestId) {
    return view != null
        && view.requests().stream()
            .filter(value -> value.id() == requestId)
            .anyMatch(
                value ->
                    value.status() == PurchaseRequestStatus.approved
                        || value.status() == PurchaseRequestStatus.rejected);
  }

  private static String decisionText(
      TelegramQuickActionResponse view, boolean approved, long requestId) {
    if (!approved) {
      return TelegramOutcomeCopy.parentRejected();
    }
    var request = view.requests().stream().filter(value -> value.id() == requestId).findFirst();
    String title = request.map(value -> TelegramViewSupport.requestTitle(value, view.locale())).orElse(
        new TelegramMessageResolver().text(view.locale(), "telegram.request.request"));
    int delta = request.map(value -> value.coins()).orElse(0);
    return TelegramOutcomeCopy.parentApproved(
        title,
        delta,
        view.balance(),
        request.map(value -> !value.requestType().isPurchase()).orElse(true));
  }

}
