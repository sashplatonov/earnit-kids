package com.sashplatonov.earnit.kids.service.family.action;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.service.event.ApplicationEventPublisher;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;

import java.util.Optional;

final class FamilyActionBalanceService {

    private final FamilyActionSupportService supportService;
    private final FamilyActionHistoryFactory historyFactory;
    private final HistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    FamilyActionBalanceService(FamilyActionSupportService supportService,
                               FamilyActionHistoryFactory historyFactory,
                               HistoryRepository historyRepository) {
        this(supportService, historyFactory, historyRepository, null);
    }

    FamilyActionBalanceService(FamilyActionSupportService supportService,
                               FamilyActionHistoryFactory historyFactory,
                               HistoryRepository historyRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.supportService = supportService;
        this.historyFactory = historyFactory;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
    }

    OperationResult<FamilyDataResponse> completeTask(String familyId, int childId, long taskId) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = supportService.findFamilyChildForUpdate(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }
        if (supportService.isInactive(child.get())) {
            return OperationResult.failure(BackendMessages.message("family.childInactive"));
        }

        Optional<TaskEntity> task = supportService.findActiveTask(familyDbId.get(), childId, taskId);
        if (task.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("tasks.notFound"));
        }

        child.get().setBalance(child.get().getBalance() + task.get().getCoins());
        historyRepository.persist(historyFactory.buildTaskHistory(familyDbId.get(), childId, task.get()));
        publish(ApplicationOutboxEventType.TASK_APPROVED, familyDbId.get(), childId, null, task.get().getCoins(), child.get().getBalance());
        return supportService.loadFamilyData(familyId, childId, true);
    }

    OperationResult<FamilyDataResponse> purchaseItem(String familyId, int childId, long itemId) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = supportService.findFamilyChildForUpdate(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }
        if (supportService.isInactive(child.get())) {
            return OperationResult.failure(BackendMessages.message("family.childInactive"));
        }

        Optional<ShopItemEntity> item = supportService.findActiveItem(familyDbId.get(), childId, itemId);
        if (item.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("shop.itemNotFound"));
        }

        if (child.get().getBalance() < item.get().getPrice()) {
            return OperationResult.failure(BackendMessages.message("balance.insufficient"));
        }

        long rewardLimit = child.get().getDailyRewardLimit();
        if (rewardLimit > 0
            && supportService.dailyRewardSpend(childId,
                historyFactory.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS)) + item.get().getPrice() > rewardLimit) {
            return OperationResult.failure(BackendMessages.message("balance.rewardLimitReached"));
        }

        child.get().setBalance(child.get().getBalance() - item.get().getPrice());
        historyRepository.persist(historyFactory.buildShopHistory(familyDbId.get(), childId, item.get()));
        publish(ApplicationOutboxEventType.REWARD_PURCHASED, familyDbId.get(), childId, null, -item.get().getPrice(), child.get().getBalance());
        return supportService.loadFamilyData(familyId, childId, true);
    }

    OperationResult<FamilyDataResponse> deleteHistoryEntry(String familyId, int childId, long historyEntryId) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = supportService.findFamilyChildForUpdate(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        Optional<HistoryEntryEntity> historyEntry = supportService.findHistoryEntry(familyDbId.get(), childId, historyEntryId);
        if (historyEntry.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("history.entryNotFound"));
        }

        int delta = historyEntry.get().getType() == HistoryEntryType.earn
            ? -historyEntry.get().getAmount()
            : historyEntry.get().getAmount();
        child.get().setBalance(child.get().getBalance() + delta);
        historyRepository.delete(historyEntry.get());
        return supportService.loadFamilyData(familyId, childId, true);
    }

    OperationResult<FamilyDataResponse> adjustBalance(String familyId,
                                                      int childId,
                                                      int amount,
                                                      String description) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }
        if (amount == 0) {
            return OperationResult.failure(BackendMessages.message("balance.amountZero"));
        }

        Optional<ChildEntity> child = supportService.findFamilyChildForUpdate(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }
        if (supportService.isInactive(child.get())) {
            return OperationResult.failure(BackendMessages.message("family.childInactive"));
        }

        child.get().setBalance(child.get().getBalance() + amount);
        historyRepository.persist(historyFactory.buildAdjustmentHistory(familyDbId.get(), childId, amount, description));
        publish(ApplicationOutboxEventType.BALANCE_ADJUSTED, familyDbId.get(), childId, null, amount, child.get().getBalance());
        return supportService.loadFamilyData(familyId, childId, true);
    }

    private void publish(ApplicationOutboxEventType type, int familyId, int childId, Long requestId,
                         int delta, Integer balance) {
        if (eventPublisher != null) {
            eventPublisher.publish(type, familyId, childId, requestId, delta, balance, historyFactory.now());
        }
    }
}
