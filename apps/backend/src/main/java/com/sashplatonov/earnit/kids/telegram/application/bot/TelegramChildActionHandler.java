package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.List;

final class TelegramChildActionHandler {
    private final TelegramQuickActionService quickActions;
    private final TelegramBotApiClient apiClient;
    private final TelegramMenuBuilder menuBuilder;

    TelegramChildActionHandler(TelegramQuickActionService quickActions,
                               TelegramBotApiClient apiClient,
                               TelegramMenuBuilder menuBuilder) {
        this.quickActions = quickActions;
        this.apiClient = apiClient;
        this.menuBuilder = menuBuilder;
    }

    void task(long telegramUserId, long taskId, JsonNode callback) {
        quickActions.load(telegramUserId, null).ifPresent(view -> {
            String taskName = view.tasks().stream()
                .filter(task -> task.id() == taskId).map(task -> task.name()).findFirst().orElse(null);
            OperationResult<TelegramQuickActionResponse> result =
                quickActions.requestTask(telegramUserId, view.childId(), taskId);
            editTaskRequestResult(callback, result, taskName, "task.request." + taskId);
        });
    }

    void reward(long telegramUserId, long rewardId, JsonNode callback) {
        quickActions.load(telegramUserId, null).ifPresent(view -> {
            OperationResult<TelegramQuickActionResponse> result =
                quickActions.requestReward(telegramUserId, view.childId(), rewardId);
            editRewardRequestResult(callback, result, "reward.request." + rewardId);
        });
    }

    private void editRewardRequestResult(JsonNode callback,
                                         OperationResult<TelegramQuickActionResponse> result,
                                         String retryData) {
        long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
        long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
        if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
            return;
        }
        boolean success = result instanceof OperationResult.Success<TelegramQuickActionResponse>;
        String text = success ? TelegramCopy.rewardWaiting() : TelegramCopy.error();
        List<TelegramBotApiClient.InlineButton> buttons = success
            ? menuBuilder.backToMain() : menuBuilder.childRetry(retryData);
        try {
            apiClient.editMessageText(chatId, messageId, text, buttons);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void editTaskRequestResult(JsonNode callback,
                                       OperationResult<TelegramQuickActionResponse> result,
                                       String taskName,
                                       String retryData) {
        long chatId = callback.path("message").path("chat").path("id").asLong(Long.MIN_VALUE);
        long messageId = callback.path("message").path("message_id").asLong(Long.MIN_VALUE);
        if (chatId == Long.MIN_VALUE || messageId == Long.MIN_VALUE) {
            return;
        }
        boolean success = result instanceof OperationResult.Success<TelegramQuickActionResponse>;
        String text = success ? TelegramCopy.waiting(taskName == null ? "Задание" : taskName) : TelegramCopy.error();
        List<TelegramBotApiClient.InlineButton> buttons = success
            ? menuBuilder.backToMain() : menuBuilder.childRetry(retryData);
        try {
            apiClient.editMessageText(chatId, messageId, text, buttons);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
