package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
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
                       TelegramMenuBuilder menuBuilder,
                       String miniAppUrl) {
        int childId = TelegramMenuFlow.coinChildId(action);
        int delta = TelegramMenuFlow.coinDelta(action.replace("apply", "confirm"));
        OperationResult<TelegramQuickActionResponse> result =
            quickActions.adjustBalance(telegramUserId, childId, delta);
        long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
        long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
        if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
            return;
        }
        List<TelegramBotApiClient.InlineButton> buttons;
        String text;
        if (result instanceof OperationResult.Success<TelegramQuickActionResponse> success) {
            TelegramQuickActionResponse view = success.value();
            text = TelegramCopy.coinApplied(delta, view.balance());
            buttons = menuBuilder.parentCoins(view, miniAppUrl);
        } else {
            TelegramQuickActionResponse snapshot = quickActions.load(telegramUserId, childId).orElse(null);
            text = TelegramCopy.error();
            buttons = snapshot != null ? menuBuilder.coinRetry(snapshot, delta) : menuBuilder.backToMain();
        }
        try {
            apiClient.editMessageText(chatId, messageId, text, buttons);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
