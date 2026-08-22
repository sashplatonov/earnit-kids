package com.sashplatonov.earnit.kids.family.application.action;

import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryType;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.ShopItemEntity;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.TaskEntity;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.history.HistoryRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.service.event.ApplicationEventPublisher;
import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventType;

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
        OperationResult<Integer> familyDbIdResult = supportService.requireFamilyDbId(familyId);
        if (familyDbIdResult.isFailure()) {
            return familyDbIdResult.asFailure();
        }
        int familyDbId = ((OperationResult.Success<Integer>) familyDbIdResult).value();

        Optional<ChildEntity> child = supportService.findFamilyChildForUpdate(familyDbId, childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }
        if (supportService.isInactive(child.get())) {
            return OperationResult.failure(BackendMessages.message("family.childInactive"));
        }

        Optional<TaskEntity> task = supportService.findActiveTask(familyDbId, childId, taskId);
        if (task.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("tasks.notFound"));
        }

        child.get().setBalance(child.get().getBalance() + task.get().getCoins());
        historyRepository.persist(historyFactory.buildTaskHistory(familyDbId, childId, task.get()));
        publish(ApplicationOutboxEventType.TASK_APPROVED, familyDbId, childId, null, task.get().getCoins(), child.get().getBalance());
        return supportService.loadFamilyData(familyId, childId, true);
    }

    OperationResult<FamilyDataResponse> purchaseItem(String familyId, int childId, long itemId) {
        OperationResult<Integer> familyDbIdResult = supportService.requireFamilyDbId(familyId);
        if (familyDbIdResult.isFailure()) {
            return familyDbIdResult.asFailure();
        }
        int familyDbId = ((OperationResult.Success<Integer>) familyDbIdResult).value();

        Optional<ChildEntity> child = supportService.findFamilyChildForUpdate(familyDbId, childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }
        if (supportService.isInactive(child.get())) {
            return OperationResult.failure(BackendMessages.message("family.childInactive"));
        }

        Optional<ShopItemEntity> item = supportService.findActiveItem(familyDbId, childId, itemId);
        if (item.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("shop.itemNotFound"));
        }

        if (child.get().getBalance() < item.get().getPrice()) {
            return OperationResult.failure(BackendMessages.message("balance.insufficient"));
        }

        long rewardLimit = child.get().getDailyRewardLimit();
        if (rewardLimit > 0
            && supportService.dailyRewardSpend(childId,
                supportService.startOfFamilyDay(familyDbId, historyFactory.now())) + item.get().getPrice() > rewardLimit) {
            return OperationResult.failure(BackendMessages.message("balance.rewardLimitReached"));
        }

        child.get().setBalance(child.get().getBalance() - item.get().getPrice());
        historyRepository.persist(historyFactory.buildShopHistory(familyDbId, childId, item.get()));
        publish(ApplicationOutboxEventType.REWARD_PURCHASED, familyDbId, childId, null, -item.get().getPrice(), child.get().getBalance());
        return supportService.loadFamilyData(familyId, childId, true);
    }

    OperationResult<FamilyDataResponse> deleteHistoryEntry(String familyId, int childId, long historyEntryId) {
        OperationResult<Integer> familyDbIdResult = supportService.requireFamilyDbId(familyId);
        if (familyDbIdResult.isFailure()) {
            return familyDbIdResult.asFailure();
        }
        int familyDbId = ((OperationResult.Success<Integer>) familyDbIdResult).value();

        Optional<ChildEntity> child = supportService.findFamilyChildForUpdate(familyDbId, childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        Optional<HistoryEntryEntity> historyEntry = supportService.findHistoryEntry(familyDbId, childId, historyEntryId);
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
        OperationResult<Integer> familyDbIdResult = supportService.requireFamilyDbId(familyId);
        if (familyDbIdResult.isFailure()) {
            return familyDbIdResult.asFailure();
        }
        int familyDbId = ((OperationResult.Success<Integer>) familyDbIdResult).value();
        if (amount == 0) {
            return OperationResult.failure(BackendMessages.message("balance.amountZero"));
        }

        Optional<ChildEntity> child = supportService.findFamilyChildForUpdate(familyDbId, childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }
        if (supportService.isInactive(child.get())) {
            return OperationResult.failure(BackendMessages.message("family.childInactive"));
        }

        child.get().setBalance(child.get().getBalance() + amount);
        historyRepository.persist(historyFactory.buildAdjustmentHistory(familyDbId, childId, amount, description));
        publish(ApplicationOutboxEventType.BALANCE_ADJUSTED, familyDbId, childId, null, amount, child.get().getBalance());
        return supportService.loadFamilyData(familyId, childId, true);
    }

    private void publish(ApplicationOutboxEventType type, int familyId, int childId, Long requestId,
                         int delta, Integer balance) {
        if (eventPublisher != null) {
            eventPublisher.publish(type, familyId, childId, requestId, delta, balance, historyFactory.now());
        }
    }
}
