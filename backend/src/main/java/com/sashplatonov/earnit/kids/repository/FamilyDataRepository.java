package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.FriendEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
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

    @Transactional
    public boolean upsertTask(int familyDbId, int childId, long taskId, String name,
                              int coins, String groupName, String frequencyJson,
                              String comment, Integer moneyLimit) {
        var existing = taskRepo.find("childId = ?1 AND taskId = ?2", childId, taskId)
            .firstResultOptional();
        if (existing.isPresent()) {
            var task = existing.get();
            task.setName(name);
            task.setCoins(coins);
            task.setGroupName(groupName);
            task.setFrequency(frequencyJson);
            task.setComment(comment);
            task.setMoneyLimit(moneyLimit);
            task.setDeleted(false);
        } else {
            taskRepo.persist(TaskEntity.builder()
                .familyId(familyDbId)
                .childId(childId)
                .taskId(taskId)
                .name(name)
                .coins(coins)
                .groupName(groupName)
                .frequency(frequencyJson)
                .comment(comment)
                .moneyLimit(moneyLimit)
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

    @Transactional
    public boolean upsertShopItem(int familyDbId, int childId, long itemId, String name,
                                  int price, String groupName, String frequencyJson,
                                  Integer moneyLimit) {
        var existing = shopRepo.find("childId = ?1 AND itemId = ?2", childId, itemId)
            .firstResultOptional();
        if (existing.isPresent()) {
            var shopItem = existing.get();
            shopItem.setName(name);
            shopItem.setPrice(price);
            shopItem.setGroupName(groupName);
            shopItem.setFrequency(frequencyJson);
            shopItem.setMoneyLimit(moneyLimit);
            shopItem.setDeleted(false);
        } else {
            shopRepo.persist(ShopItemEntity.builder()
                .familyId(familyDbId)
                .childId(childId)
                .itemId(itemId)
                .name(name)
                .price(price)
                .groupName(groupName)
                .frequency(frequencyJson)
                .moneyLimit(moneyLimit)
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
        return historyRepo.find("childId = ?1 ORDER BY createdAt DESC", childId)
            .range(offset, offset + limit - 1)
            .list();
    }

    public int getHistoryCount(int childId) {
        return (int) historyRepo.count("childId = ?1", childId);
    }

    @Transactional
    public boolean addHistory(int familyDbId, int childId, long externalId, String type,
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

    public List<PurchaseRequestEntity> getRequests(int familyDbId, int limit, int offset) {
        return requestRepo.find("familyId = ?1 ORDER BY createdAt DESC", familyDbId)
            .range(offset, offset + limit - 1)
            .list();
    }

    public int getRequestsCount(int familyDbId) {
        return (int) requestRepo.count("familyId = ?1", familyDbId);
    }

    @Transactional
    public boolean createRequest(int familyDbId, int childId, long externalId,
                                 Long taskId, String taskName, Long itemId,
                                 int coins, String requestType, int moneyAmount) {
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
    public boolean updateRequestStatus(int requestId, String status) {
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
