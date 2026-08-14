package com.sashplatonov.earnit.kids.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

final class TelegramCoinAdjustmentHandler {
    private TelegramCoinAdjustmentHandler() {
    }

    static void handle(long telegramUserId,
                       String action,
                       JsonNode callback,
                       TelegramQuickActionService quickActions,
                       TelegramBotApiClient apiClient,
                       TelegramMenuBuilder menuBuilder) {
        int childId = TelegramMenuFlow.coinChildId(action);
        int delta = TelegramMenuFlow.coinDelta(action.replace("apply", "confirm"));
        OperationResult<TelegramQuickActionResponse> result =
            quickActions.adjustBalance(telegramUserId, childId, delta);
        long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
        long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
        if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
            return;
        }
        String text = result instanceof OperationResult.Success<TelegramQuickActionResponse> success
            ? "✅ Balance updated · " + success.value().balance() + " 🪙"
            : "Could not update balance. Refresh and try again.";
        try {
            apiClient.editMessageText(chatId, messageId, text, menuBuilder.backToMain());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
