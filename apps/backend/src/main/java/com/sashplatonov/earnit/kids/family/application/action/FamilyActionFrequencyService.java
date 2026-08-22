package com.sashplatonov.earnit.kids.family.application.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.ShopItemEntity;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.TaskEntity;
import com.sashplatonov.earnit.kids.family.api.request.FrequencyPeriod;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.history.HistoryRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.request.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZoneId;

final class FamilyActionFrequencyService {

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final HistoryRepository historyRepository;
    private final FamilyRepository familyRepository;
    private final TimeProvider timeProvider;
    private final FrequencyWindowService frequencyWindowService;

    FamilyActionFrequencyService(PurchaseRequestRepository purchaseRequestRepository,
                                 HistoryRepository historyRepository,
                                 FamilyRepository familyRepository,
                                 TimeProvider timeProvider,
                                 FrequencyWindowService frequencyWindowService) {
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.historyRepository = historyRepository;
        this.familyRepository = familyRepository;
        this.timeProvider = timeProvider;
        this.frequencyWindowService = frequencyWindowService;
    }

    String validateTaskRequestLimit(int familyDbId, int childId, TaskEntity task) {
        ZoneId zoneId = zoneId(familyDbId);
        var window = frequencyWindowService.resolveCurrentWindow(task.getFrequency(), now(), zoneId);
        if (window.isEmpty()) {
            return null;
        }
        FrequencyWindow currentWindow = window.get();
        long usedCount = purchaseRequestRepository.countPendingTaskRequestsInWindow(
            familyDbId, childId, task.getTaskId(), currentWindow.start(), currentWindow.end()
        ) + historyRepository.countTaskEarnsInWindow(
            familyDbId, childId, task.getTaskId(), currentWindow.start(), currentWindow.end()
        );

        return usedCount >= currentWindow.limit() ? BackendMessages.taskLimitReached(currentWindow.period(),
            frequencyWindowService.formatResetAt(currentWindow.end(), currentWindow.period(), zoneId)) : null;
    }

    String validateItemRequestLimit(int familyDbId, int childId, ShopItemEntity item) {
        ZoneId zoneId = zoneId(familyDbId);
        var window = frequencyWindowService.resolveCurrentWindow(item.getFrequency(), now(), zoneId);
        if (window.isEmpty()) {
            return null;
        }
        FrequencyWindow currentWindow = window.get();
        long usedCount = purchaseRequestRepository.countPendingItemRequestsInWindow(
            familyDbId, childId, item.getItemId(), currentWindow.start(), currentWindow.end()
        ) + historyRepository.countShopPurchasesInWindow(
            familyDbId, childId, item.getItemId(), currentWindow.start(), currentWindow.end()
        );

        return usedCount >= currentWindow.limit() ? BackendMessages.itemLimitReached(currentWindow.period(),
            frequencyWindowService.formatResetAt(currentWindow.end(), currentWindow.period(), zoneId)) : null;
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

    private ZoneId zoneId(int familyDbId) {
        try {
            return ZoneId.of(familyRepository.getTimezone(familyDbId).orElse("UTC"));
        } catch (DateTimeException ignored) {
            return ZoneId.of("UTC");
        }
    }
}
