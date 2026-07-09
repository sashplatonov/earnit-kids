package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.FriendEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.service.SlowOperationDiagnostics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyDataRepository {

    private final TaskRepository taskRepo;
    private final ShopItemRepository shopRepo;
    private final HistoryRepository historyRepo;
    private final PurchaseRequestRepository requestRepo;
    private final FriendRepository friendRepo;
    private final SlowOperationDiagnostics slowOperationDiagnostics;

    public List<TaskEntity> getTasks(int childId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getTasks",
            () -> taskRepo.list("childId = ?1 AND deleted = false ORDER BY id ASC", childId),
            "childId",
            String.valueOf(childId)
        );
    }

    public List<TaskEntity> getTasksForFamily(int familyDbId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getTasksForFamily",
            () -> taskRepo.list("familyId = ?1 AND deleted = false ORDER BY id ASC", familyDbId),
            "familyDbId",
            String.valueOf(familyDbId)
        );
    }

    @Transactional
    public void markAllTasksDeleted(int childId) {
        taskRepo.update("deleted = true where childId = ?1", childId);
    }

    @Transactional
    public boolean upsertTask(TaskUpsertCommand command) {
        var existing = taskRepo.find("childId = ?1 AND taskId = ?2", command.childId(), command.taskId())
            .firstResultOptional();
        if (existing.isPresent()) {
            var task = existing.get();
            task.setName(command.name());
            task.setCoins(command.coins());
            task.setGroupName(command.groupName());
            task.setFrequency(command.frequency());
            task.setComment(command.comment());
            task.setMoneyLimit(command.moneyLimit());
            task.setActive(command.active());
            task.setDeleted(command.deleted());
        } else {
            taskRepo.persist(TaskEntity.builder()
                .familyId(command.familyDbId())
                .childId(command.childId())
                .taskId(command.taskId())
                .name(command.name())
                .coins(command.coins())
                .groupName(command.groupName())
                .frequency(command.frequency())
                .comment(command.comment())
                .moneyLimit(command.moneyLimit())
                .active(command.active())
                .deleted(command.deleted())
                .build());
        }
        return true;
    }

    @Transactional
    public boolean softDeleteTask(int childId, long taskId) {
        return taskRepo.find("childId = ?1 AND taskId = ?2", childId, taskId)
            .firstResultOptional()
            .map(t -> {
                t.setDeleted(true);
                return true;
            })
            .orElse(false);
    }

    public List<ShopItemEntity> getShopItems(int childId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getShopItems",
            () -> shopRepo.list("childId = ?1 AND deleted = false ORDER BY id ASC", childId),
            "childId",
            String.valueOf(childId)
        );
    }

    public List<ShopItemEntity> getShopItemsForFamily(int familyDbId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getShopItemsForFamily",
            () -> shopRepo.list("familyId = ?1 AND deleted = false ORDER BY id ASC", familyDbId),
            "familyDbId",
            String.valueOf(familyDbId)
        );
    }

    @Transactional
    public void markAllShopItemsDeleted(int childId) {
        shopRepo.update("deleted = true where childId = ?1", childId);
    }

    @Transactional
    public boolean upsertShopItem(ShopItemUpsertCommand command) {
        var existing = shopRepo.find("childId = ?1 AND itemId = ?2", command.childId(), command.itemId())
            .firstResultOptional();
        if (existing.isPresent()) {
            var shopItem = existing.get();
            shopItem.setName(command.name());
            shopItem.setPrice(command.price());
            shopItem.setGroupName(command.groupName());
            shopItem.setFrequency(command.frequency());
            shopItem.setComment(command.comment());
            shopItem.setMoneyLimit(command.moneyLimit());
            shopItem.setActive(command.active());
            shopItem.setDeleted(command.deleted());
        } else {
            shopRepo.persist(ShopItemEntity.builder()
                .familyId(command.familyDbId())
                .childId(command.childId())
                .itemId(command.itemId())
                .name(command.name())
                .price(command.price())
                .groupName(command.groupName())
                .frequency(command.frequency())
                .comment(command.comment())
                .moneyLimit(command.moneyLimit())
                .active(command.active())
                .deleted(command.deleted())
                .build());
        }
        return true;
    }

    @Transactional
    public boolean softDeleteShopItem(int childId, long itemId) {
        return shopRepo.find("childId = ?1 AND itemId = ?2", childId, itemId)
            .firstResultOptional()
            .map(s -> {
                s.setDeleted(true);
                return true;
            })
            .orElse(false);
    }

    public List<HistoryEntryEntity> getHistory(int childId, int limit, int offset) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getHistory",
            () -> historyRepo.find("childId = ?1 ORDER BY createdAt DESC, id DESC", childId)
                .range(offset, offset + limit - 1)
                .list(),
            "childId",
            String.valueOf(childId),
            "limit",
            String.valueOf(limit),
            "offset",
            String.valueOf(offset)
        );
    }

    public List<HistoryEntryEntity> getHistoryForFamily(int familyDbId, int limit, int offset) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getHistoryForFamily",
            () -> historyRepo.find("familyId = ?1 ORDER BY createdAt DESC, id DESC", familyDbId)
                .range(offset, offset + limit - 1)
                .list(),
            "familyDbId",
            String.valueOf(familyDbId),
            "limit",
            String.valueOf(limit),
            "offset",
            String.valueOf(offset)
        );
    }

    public List<HistoryEntryEntity> getAllHistoryForFamily(int familyDbId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getAllHistoryForFamily",
            () -> historyRepo.list("familyId = ?1 ORDER BY createdAt DESC, id DESC", familyDbId),
            "familyDbId",
            String.valueOf(familyDbId)
        );
    }

    public int getHistoryCount(int childId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getHistoryCount",
            () -> (int) historyRepo.count("childId = ?1", childId),
            "childId",
            String.valueOf(childId)
        );
    }

    @Transactional
    public boolean addHistory(int familyDbId, int childId, long externalId, HistoryEntryType type,
                              int amount, String description, int moneyAmount,
                              Long relatedId, String groupName, String comment) {
        historyRepo.persist(HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(externalId)
            .type(type)
            .amount(amount)
            .description(description)
            .moneyAmount(moneyAmount)
            .relatedId(relatedId)
            .groupName(groupName)
            .comment(comment)
            .build());
        return true;
    }

    @Transactional
    public void replaceHistory(int familyDbId, int childId, List<HistoryEntryEntity> entries) {
        historyRepo.delete("familyId = ?1 AND childId = ?2", familyDbId, childId);
        entries.forEach(historyRepo::persist);
    }

    @Transactional
    public void upsertHistoryEntry(HistoryEntryEntity entry) {
        if (entry.getExternalId() != null) {
            historyRepo.delete("familyId = ?1 AND externalId = ?2", entry.getFamilyId(), entry.getExternalId());
        }
        historyRepo.persist(entry);
    }

    public List<PurchaseRequestEntity> getRequests(int familyDbId, int limit, int offset) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getRequests",
            () -> requestRepo.find("familyId = ?1 ORDER BY createdAt DESC, id DESC", familyDbId)
                .range(offset, offset + limit - 1)
                .list(),
            "familyDbId",
            String.valueOf(familyDbId),
            "limit",
            String.valueOf(limit),
            "offset",
            String.valueOf(offset)
        );
    }

    public List<PurchaseRequestEntity> getAllRequestsForFamily(int familyDbId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getAllRequestsForFamily",
            () -> requestRepo.list("familyId = ?1", familyDbId),
            "familyDbId",
            String.valueOf(familyDbId)
        );
    }

    public int getRequestsCount(int familyDbId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getRequestsCount",
            () -> (int) requestRepo.count("familyId = ?1", familyDbId),
            "familyDbId",
            String.valueOf(familyDbId)
        );
    }

    @Transactional
    public boolean createRequest(int familyDbId, int childId, long externalId,
                                 Long taskId, String taskName, Long itemId,
                                 int coins, PurchaseRequestType requestType, int moneyAmount) {
        requestRepo.persist(PurchaseRequestEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(externalId)
            .taskId(taskId)
            .taskName(taskName)
            .itemId(itemId)
            .coins(coins)
            .requestType(requestType)
            .moneyAmount(moneyAmount)
            .build());
        return true;
    }

    @Transactional
    public void replaceRequests(int familyDbId, List<PurchaseRequestEntity> entries) {
        requestRepo.delete("familyId = ?1", familyDbId);
        entries.forEach(requestRepo::persist);
    }

    @Transactional
    public boolean updateRequestStatus(int requestId, PurchaseRequestStatus status) {
        return requestRepo.findByIdOptional((long) requestId)
            .map(r -> {
                r.setStatus(status);
                return true;
            })
            .orElse(false);
    }

    public List<Integer> getFriendChildIds(int childId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getFriendChildIds",
            () -> friendRepo.find("childId = ?1", childId)
                .stream()
                .map(FriendEntity::getFriendChildId)
                .toList(),
            "childId",
            String.valueOf(childId)
        );
    }

    @Transactional
    public boolean addFriend(int childId, int friendChildId) {
        if (friendRepo.count("childId = ?1 AND friendChildId = ?2", childId, friendChildId) == 0) {
            friendRepo.persist(FriendEntity.builder()
                .childId(childId)
                .friendChildId(friendChildId)
                .build());
        }
        if (friendRepo.count("childId = ?1 AND friendChildId = ?2", friendChildId, childId) == 0) {
            friendRepo.persist(FriendEntity.builder()
                .childId(friendChildId)
                .friendChildId(childId)
                .build());
        }
        return true;
    }
}
