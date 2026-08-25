package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

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
        TelegramQuickActionResponse snapshot = quickActions.load(telegramUserId, childId).orElse(null);
        OperationResult<TelegramQuickActionResponse> result =
            quickActions.adjustBalance(telegramUserId, childId, delta);
        long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
        long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
        if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
            return;
        }
        try {
            TelegramQuickActionResponse view = result instanceof OperationResult.Success<TelegramQuickActionResponse> success
                ? success.value() : snapshot;
            FamilyLocale locale = view == null ? FamilyLocale.en : view.locale();
            TelegramLocaleContext.with(locale, () -> {
                if (result instanceof OperationResult.Success<TelegramQuickActionResponse> success) {
                    TelegramQuickActionResponse successView = success.value();
                    apiClient.editMessageText(chatId, messageId,
                        TelegramCopy.coinApplied(delta, successView.balance()),
                        menuBuilder.parentCoins(successView, miniAppUrl));
                } else {
                    apiClient.editMessageText(chatId, messageId, TelegramOutcomeCopy.error(),
                        snapshot != null ? menuBuilder.coinRetry(snapshot, delta) : menuBuilder.backToMain());
                }
            });
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
