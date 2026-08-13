package com.sashplatonov.earnit.kids.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.util.OperationResult;

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
        TelegramQuickActionResponse currentView = result instanceof OperationResult.Success<TelegramQuickActionResponse> success
            ? success.value() : quickActions.load(telegramUserId, childId).orElse(null);
        apiClient.editMessageText(chatId, messageId,
            decisionText(result, approved, requestId, childId, telegramUserId, quickActions),
            currentView == null ? menuBuilder.backToMain() : menuBuilder.backToMain(currentView));
    }

    private static String decisionText(OperationResult<TelegramQuickActionResponse> result,
                                       boolean approved,
                                       long requestId,
                                       int childId,
                                       long telegramUserId,
                                       TelegramQuickActionService quickActions) {
        if (!(result instanceof OperationResult.Success<TelegramQuickActionResponse> success)) {
            var refreshed = quickActions.load(telegramUserId, childId);
            if (refreshed.isPresent()) {
                var request = refreshed.get().requests().stream()
                    .filter(value -> value.id() == requestId).findFirst();
                if (request.isPresent() && request.get().status() == PurchaseRequestStatus.approved) {
                    return "Already approved";
                }
                if (request.isPresent() && request.get().status() == PurchaseRequestStatus.rejected) {
                    return "Already rejected";
                }
            }
            return "Request already processed or unavailable. Refresh the list.";
        }
        if (!approved) {
            return "❌ Rejected by you · Request " + requestId;
        }
        return "✅ Approved by you · Balance: " + success.value().balance() + " 🪙";
    }
}
