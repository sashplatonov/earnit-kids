package com.sashplatonov.earnit.kids.service.family.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.request.FrequencyPeriod;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;

import java.time.Instant;

final class FamilyActionFrequencyService {

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final HistoryRepository historyRepository;
    private final TimeProvider timeProvider;
    private final FrequencyWindowService frequencyWindowService;

    FamilyActionFrequencyService(PurchaseRequestRepository purchaseRequestRepository,
                                 HistoryRepository historyRepository,
                                 TimeProvider timeProvider) {
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.historyRepository = historyRepository;
        this.timeProvider = timeProvider;
        this.frequencyWindowService = new FrequencyWindowService();
    }

    String validateTaskRequestLimit(int familyDbId, int childId, TaskEntity task) {
        Integer limit = frequencyWindowService.extractFrequencyLimit(task.getFrequency());
        if (limit == null) {
            return null;
        }

        String period = frequencyWindowService.extractFrequencyPeriod(task.getFrequency());
        Instant windowStart = frequencyWindowService.currentPeriodStart(now(), period);
        Instant windowEnd = frequencyWindowService.nextPeriodStart(windowStart, period);
        long usedCount = purchaseRequestRepository.countPendingTaskRequestsInWindow(
            familyDbId, childId, task.getTaskId(), windowStart, windowEnd
        ) + historyRepository.countTaskEarnsInWindow(
            familyDbId, childId, task.getTaskId(), windowStart, windowEnd
        );

        return usedCount >= limit ? BackendMessages.taskLimitReached(period,
            frequencyWindowService.formatResetAt(windowEnd, period)) : null;
    }

    String validateItemRequestLimit(int familyDbId, int childId, ShopItemEntity item) {
        Integer limit = frequencyWindowService.extractFrequencyLimit(item.getFrequency());
        if (limit == null) {
            return null;
        }

        String period = frequencyWindowService.extractFrequencyPeriod(item.getFrequency());
        Instant windowStart = frequencyWindowService.currentPeriodStart(now(), period);
        Instant windowEnd = frequencyWindowService.nextPeriodStart(windowStart, period);
        long usedCount = purchaseRequestRepository.countPendingItemRequestsInWindow(
            familyDbId, childId, item.getItemId(), windowStart, windowEnd
        ) + historyRepository.countShopPurchasesInWindow(
            familyDbId, childId, item.getItemId(), windowStart, windowEnd
        );

        return usedCount >= limit ? BackendMessages.itemLimitReached(period,
            frequencyWindowService.formatResetAt(windowEnd, period)) : null;
    }

    JsonNode buildFrequencyNode(Integer limit, FrequencyPeriod period) {
        if (limit == null || limit <= 0 || !frequencyWindowService.isValidFrequencyPeriod(period)) {
            return null;
        }
        return new ObjectMapper().createObjectNode()
            .put("limit", limit)
            .put("period", period.name());
    }

    private Instant now() {
        return timeProvider.now();
    }
}
