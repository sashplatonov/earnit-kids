package com.sashplatonov.earnit.kids.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.List;

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
        List<TelegramBotApiClient.InlineButton> buttons = menuBuilder.backToMain();
        String text;
        if (result instanceof OperationResult.Success<TelegramQuickActionResponse> success) {
            text = TelegramBotEmoji.DONE + " Balance updated · " + success.value().balance() + " " + TelegramBotEmoji.COINS;
        } else {
            TelegramQuickActionResponse snapshot = quickActions.load(telegramUserId, childId).orElse(null);
            text = "Could not update balance. Current value was not changed.";
            if (snapshot != null) {
                text = "Balance update failed. Current balance: " + snapshot.balance() + " " + TelegramBotEmoji.COINS;
                buttons = menuBuilder.coinRetry(snapshot, delta);
            }
        }
        try {
            apiClient.editMessageText(chatId, messageId, text, buttons);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
