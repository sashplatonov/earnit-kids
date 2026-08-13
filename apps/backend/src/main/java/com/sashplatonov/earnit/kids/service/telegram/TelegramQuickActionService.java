package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.Optional;

public interface TelegramQuickActionService {
    Optional<TelegramQuickActionResponse> load(long telegramUserId, Integer selectedChildId);

    OperationResult<TelegramQuickActionResponse> requestTask(long telegramUserId, int childId, long taskId);

    OperationResult<TelegramQuickActionResponse> requestReward(long telegramUserId, int childId, long rewardId);

    OperationResult<TelegramQuickActionResponse> approveRequest(long telegramUserId, int childId, long requestId);

    OperationResult<TelegramQuickActionResponse> rejectRequest(long telegramUserId, int childId, long requestId);

    OperationResult<TelegramQuickActionResponse> adjustBalance(long telegramUserId, int childId, int amount);
}
