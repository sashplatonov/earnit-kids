package com.sashplatonov.earnit.kids.family.application.action;

import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryType;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestType;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.ShopItemEntity;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.TaskEntity;
import com.sashplatonov.earnit.kids.util.TimeProvider;

import java.time.Instant;
import java.util.Optional;

final class FamilyActionHistoryFactory {

    private final TimeProvider timeProvider;

    FamilyActionHistoryFactory(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    HistoryEntryEntity buildTaskHistory(int familyDbId, int childId, TaskEntity task) {
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .type(HistoryEntryType.earn)
            .amount(task.getCoins())
            .description(task.getName())
            .moneyAmount(0)
            .relatedId(task.getTaskId())
            .groupName(task.getGroupName())
            .comment(task.getComment())
            .createdAt(now())
            .build();
    }

    HistoryEntryEntity buildShopHistory(int familyDbId, int childId, ShopItemEntity item) {
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .type(HistoryEntryType.spend)
            .amount(item.getPrice())
            .description(item.getName())
            .moneyAmount(item.getMoneyLimit() != null ? item.getMoneyLimit() : 0)
            .relatedId(item.getItemId())
            .groupName(item.getGroupName())
            .comment(item.getComment())
            .createdAt(now())
            .build();
    }

    HistoryEntryEntity buildRequestHistory(int familyDbId,
                                           PurchaseRequestEntity request,
                                           Optional<ShopItemEntity> item,
                                           Optional<TaskEntity> task) {
        Instant requestCreatedAt = request.getCreatedAt() != null ? request.getCreatedAt() : now();
        if (request.getRequestType() == PurchaseRequestType.shop_purchase) {
            return HistoryEntryEntity.builder()
                .familyId(familyDbId)
                .childId(request.getChildId())
                .externalId(nextExternalId())
                .type(HistoryEntryType.spend)
                .amount(request.getCoins())
                .description(item.map(ShopItemEntity::getName).orElse(request.getTaskName()))
                .moneyAmount(request.getMoneyAmount())
                .relatedId(request.getItemId() != null ? request.getItemId() : request.getTaskId())
                .groupName(item.map(ShopItemEntity::getGroupName).orElse(null))
                .comment(item.map(ShopItemEntity::getComment).orElse(null))
                .createdAt(requestCreatedAt)
                .build();
        }

        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(request.getChildId())
            .externalId(nextExternalId())
            .type(HistoryEntryType.earn)
            .amount(request.getCoins())
            .description(task.map(TaskEntity::getName).orElse(request.getTaskName()))
            .moneyAmount(0)
            .relatedId(request.getTaskId())
            .groupName(task.map(TaskEntity::getGroupName).orElse(null))
            .comment(task.map(TaskEntity::getComment).orElse(null))
            .createdAt(requestCreatedAt)
            .build();
    }

    HistoryEntryEntity buildAdjustmentHistory(int familyDbId, int childId, int amount, String description) {
        String normalizedDescription = description != null && !description.isBlank()
            ? description.trim()
            : amount > 0
                ? com.sashplatonov.earnit.kids.i18n.BackendMessages.message("balance.adjustmentCredit")
                : com.sashplatonov.earnit.kids.i18n.BackendMessages.message("balance.adjustmentDebit");
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .type(amount > 0 ? HistoryEntryType.earn : HistoryEntryType.spend)
            .amount(Math.abs(amount))
            .description(normalizedDescription)
            .moneyAmount(0)
            .createdAt(now())
            .build();
    }

    Instant now() {
        return timeProvider.now();
    }

    long nextExternalId() {
        long value = timeProvider.now().toEpochMilli();
        return value > 0 ? value : 1L;
    }
}
