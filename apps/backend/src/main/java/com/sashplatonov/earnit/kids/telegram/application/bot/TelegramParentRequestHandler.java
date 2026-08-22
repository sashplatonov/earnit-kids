package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.family.api.response.RequestDto;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.List;

final class TelegramParentRequestHandler {
    private TelegramParentRequestHandler() {
    }

    static void handle(long telegramUserId,
                       String data,
                       JsonNode callback,
                       TelegramQuickActionService quickActions,
                       TelegramBotApiClient apiClient,
                       TelegramMenuBuilder menuBuilder) throws Exception {
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
        OperationResult<TelegramQuickActionResponse> result = approved
            ? quickActions.approveRequest(telegramUserId, childId, requestId)
            : quickActions.rejectRequest(telegramUserId, childId, requestId);
        editResult(callback, result, approved, requestId, childId, telegramUserId,
            quickActions, apiClient, menuBuilder, queueContext, data);
    }

    private static void editResult(JsonNode callback,
                                   OperationResult<TelegramQuickActionResponse> result,
                                   boolean approved,
                                   long requestId,
                                   int childId,
                                   long telegramUserId,
                                   TelegramQuickActionService quickActions,
                                   TelegramBotApiClient apiClient,
                                   TelegramMenuBuilder menuBuilder,
                                   boolean queueContext,
                                   String retryData) throws Exception {
        long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
        long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
        if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
            return;
        }
        if (result instanceof OperationResult.Success<TelegramQuickActionResponse> success) {
            if (queueContext) {
                editQueueAdvance(callback, success.value(), apiClient, menuBuilder);
            } else {
                apiClient.editMessageText(chatId, messageId,
                    decisionText(success.value(), approved, requestId), List.of());
            }
            return;
        }
        TelegramQuickActionResponse view = quickActions.load(telegramUserId, childId).orElse(null);
        if (isStale(view, requestId)) {
            apiClient.editMessageText(chatId, messageId, TelegramCopy.stale(), List.of());
            return;
        }
        apiClient.editMessageText(chatId, messageId, TelegramCopy.error(),
            view == null ? menuBuilder.backToMain() : menuBuilder.parentRequestRetry(view, retryData));
    }

    private static void editQueueAdvance(JsonNode callback,
                                         TelegramQuickActionResponse view,
                                         TelegramBotApiClient apiClient,
                                         TelegramMenuBuilder menuBuilder) throws Exception {
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
        apiClient.editMessageText(chatId, messageId, TelegramCopy.emptyRequests(), List.of());
    }

    private static String queueTextFor(TelegramQuickActionResponse view) {
        List<RequestDto> pending = TelegramViewSupport.pendingRequests(view);
        if (pending.isEmpty()) {
            return TelegramCopy.emptyRequests();
        }
        RequestDto request = pending.get(0);
        return TelegramCopy.requestQueueText(view.childName(), TelegramViewSupport.requestTitle(request),
            request.coins(), !request.requestType().isPurchase(), 1, pending.size());
    }

    private static boolean isStale(TelegramQuickActionResponse view, long requestId) {
        return view != null && view.requests().stream()
            .filter(value -> value.id() == requestId)
            .anyMatch(value -> value.status() == PurchaseRequestStatus.approved
                || value.status() == PurchaseRequestStatus.rejected);
    }

    private static String decisionText(TelegramQuickActionResponse view, boolean approved, long requestId) {
        if (!approved) {
            return TelegramCopy.parentRejected();
        }
        var request = view.requests().stream().filter(value -> value.id() == requestId).findFirst();
        String title = request.map(TelegramParentRequestHandler::title).orElse(null);
        int delta = request.map(value -> value.coins()).orElse(0);
        return TelegramCopy.parentApproved(title == null ? "Запрос" : title, delta, view.balance(),
            request.map(value -> !value.requestType().isPurchase()).orElse(true));
    }

    private static String title(RequestDto request) {
        return request.title() != null ? request.title()
            : request.taskName() != null ? request.taskName()
            : request.itemName();
    }
}
