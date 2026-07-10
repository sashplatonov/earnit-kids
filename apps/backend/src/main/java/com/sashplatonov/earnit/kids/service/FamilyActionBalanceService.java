package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.Optional;

final class FamilyActionBalanceService {

    private final FamilyActionSupportService supportService;
    private final FamilyActionHistoryFactory historyFactory;
    private final HistoryRepository historyRepository;

    FamilyActionBalanceService(FamilyActionSupportService supportService,
                               FamilyActionHistoryFactory historyFactory,
                               HistoryRepository historyRepository) {
        this.supportService = supportService;
        this.historyFactory = historyFactory;
        this.historyRepository = historyRepository;
    }

    OperationResult<FamilyDataResponse> completeTask(String familyId, int childId, long taskId) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = supportService.findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        Optional<TaskEntity> task = supportService.findActiveTask(familyDbId.get(), childId, taskId);
        if (task.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("tasks.notFound"));
        }

        child.get().setBalance(child.get().getBalance() + task.get().getCoins());
        historyRepository.persist(historyFactory.buildTaskHistory(familyDbId.get(), childId, task.get()));
        return supportService.loadFamilyData(familyId, childId, true);
    }

    OperationResult<FamilyDataResponse> purchaseItem(String familyId, int childId, long itemId) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = supportService.findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        Optional<ShopItemEntity> item = supportService.findActiveItem(familyDbId.get(), childId, itemId);
        if (item.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("shop.itemNotFound"));
        }

        if (child.get().getBalance() < item.get().getPrice()) {
            return OperationResult.failure(BackendMessages.message("balance.insufficient"));
        }

        child.get().setBalance(child.get().getBalance() - item.get().getPrice());
        historyRepository.persist(historyFactory.buildShopHistory(familyDbId.get(), childId, item.get()));
        return supportService.loadFamilyData(familyId, childId, true);
    }

    OperationResult<FamilyDataResponse> deleteHistoryEntry(String familyId, int childId, long historyEntryId) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = supportService.findFamilyChild(familyDbId.get(), childId);
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

        Optional<ChildEntity> child = supportService.findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        child.get().setBalance(child.get().getBalance() + amount);
        historyRepository.persist(historyFactory.buildAdjustmentHistory(familyDbId.get(), childId, amount, description));
        return supportService.loadFamilyData(familyId, childId, true);
    }
}
