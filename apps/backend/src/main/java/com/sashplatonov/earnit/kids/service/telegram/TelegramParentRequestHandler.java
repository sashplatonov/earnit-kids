package com.sashplatonov.earnit.kids.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
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
        if (parts.length != 5 || (!"approve".equals(parts[2]) && !"reject".equals(parts[2]))) {
            return;
        }
        int childId = Integer.parseInt(parts[3]);
        long requestId = Long.parseLong(parts[4]);
        boolean approved = "approve".equals(parts[2]);
        OperationResult<TelegramQuickActionResponse> result = approved
            ? quickActions.approveRequest(telegramUserId, childId, requestId)
            : quickActions.rejectRequest(telegramUserId, childId, requestId);
        editResult(callback, result, approved, requestId, childId, telegramUserId,
            quickActions, apiClient, menuBuilder);
    }

    private static void editResult(JsonNode callback,
                                   OperationResult<TelegramQuickActionResponse> result,
                                   boolean approved,
                                   long requestId,
                                   int childId,
                                   long telegramUserId,
                                   TelegramQuickActionService quickActions,
                                   TelegramBotApiClient apiClient,
                                   TelegramMenuBuilder menuBuilder) throws Exception {
        long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
        long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
        if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
            return;
        }
        // EXPLAIN: One callback completes the decision; the same message is
        // EXPLAIN: edited to a terminal resolved state and decision buttons disappear.
        if (result instanceof OperationResult.Success<TelegramQuickActionResponse> success) {
            apiClient.editMessageText(chatId, messageId, decisionText(success.value(), approved, requestId), List.of());
            return;
        }
        TelegramQuickActionResponse view = quickActions.load(telegramUserId, childId).orElse(null);
        if (isStale(view, requestId)) {
            apiClient.editMessageText(chatId, messageId, TelegramCopy.stale(), List.of());
            return;
        }
        apiClient.editMessageText(chatId, messageId, TelegramCopy.error(),
            view == null ? menuBuilder.backToMain() : menuBuilder.backToMain(view));
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
        return TelegramCopy.parentApproved(title == null ? "Запрос" : title, delta, view.balance());
    }

    private static String title(RequestDto request) {
        return request.title() != null ? request.title()
            : request.taskName() != null ? request.taskName()
            : request.itemName();
    }
}
