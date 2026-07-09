package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.FriendEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
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

    public List<TaskEntity> getTasks(int childId) {
        return taskRepo.list("childId = ?1 AND deleted = false ORDER BY id ASC", childId);
    }

    public List<TaskEntity> getTasksForFamily(int familyDbId) {
        return taskRepo.list("familyId = ?1 AND deleted = false ORDER BY id ASC", familyDbId);
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
        return shopRepo.list("childId = ?1 AND deleted = false ORDER BY id ASC", childId);
    }

    public List<ShopItemEntity> getShopItemsForFamily(int familyDbId) {
        return shopRepo.list("familyId = ?1 AND deleted = false ORDER BY id ASC", familyDbId);
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
        return historyRepo.find("childId = ?1 ORDER BY createdAt DESC, id DESC", childId)
            .range(offset, offset + limit - 1)
            .list();
    }

    public List<HistoryEntryEntity> getHistoryForFamily(int familyDbId, int limit, int offset) {
        return historyRepo.find("familyId = ?1 ORDER BY createdAt DESC, id DESC", familyDbId)
            .range(offset, offset + limit - 1)
            .list();
    }

    public List<HistoryEntryEntity> getAllHistoryForFamily(int familyDbId) {
        return historyRepo.list("familyId = ?1 ORDER BY createdAt DESC, id DESC", familyDbId);
    }

    public int getHistoryCount(int childId) {
        return (int) historyRepo.count("childId = ?1", childId);
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
        return requestRepo.find("familyId = ?1 ORDER BY createdAt DESC, id DESC", familyDbId)
            .range(offset, offset + limit - 1)
            .list();
    }

    public List<PurchaseRequestEntity> getAllRequestsForFamily(int familyDbId) {
        return requestRepo.list("familyId = ?1", familyDbId);
    }

    public int getRequestsCount(int familyDbId) {
        return (int) requestRepo.count("familyId = ?1", familyDbId);
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
        return friendRepo.find("childId = ?1", childId)
            .stream()
            .map(FriendEntity::getFriendChildId)
            .toList();
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
