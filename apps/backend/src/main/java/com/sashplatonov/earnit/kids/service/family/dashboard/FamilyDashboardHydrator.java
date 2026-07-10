package com.sashplatonov.earnit.kids.service.family.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FriendRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.service.family.FamilyRelatedDetailsResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyDashboardHydrator {
    private final HistoryRepository historyRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final FriendRepository friendRepository;
    private final ChildRepository childRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final FamilyDashboardMapper mapper;
    private final ObjectMapper objectMapper;

    List<HistoryEntryDto> loadHistory(int familyDbId, int childId,
                                      Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> taskMap,
                                      Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopMap) {
        List<HistoryEntryEntity> rows = historyRepository.getHistory(childId, 50, 0);
        hydrateMissingHistoryEntries(familyDbId, childId, rows, taskMap, shopMap);
        return rows.stream()
            .map(historyEntry -> toHistoryDto(historyEntry, taskMap, shopMap))
            .toList();
    }

    List<RequestDto> loadRequests(int familyDbId,
                                  int activeChildId,
                                  boolean adminSession,
                                  Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> taskMap,
                                  Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopMap) {
        List<PurchaseRequestEntity> rows = purchaseRequestRepository.getRequests(familyDbId, 50, 0);
        hydrateMissingRequests(familyDbId, rows, taskMap, shopMap);
        return rows.stream()
            .filter(request -> adminSession || Objects.equals(request.getChildId(), activeChildId))
            .map(request -> toRequestDto(
                request,
                Objects.equals(request.getChildId(), activeChildId) ? taskMap : Map.of(),
                Objects.equals(request.getChildId(), activeChildId) ? shopMap : Map.of()
            ))
            .toList();
    }

    List<FriendDto> loadFriends(int childId) {
        var friendIds = friendRepository.getFriendChildIds(childId);
        return childRepository.findByChildIds(friendIds).stream()
            .map(friend -> new FriendDto(friend.getId(), friend.getName(), friend.getBalance()))
            .toList();
    }

    private void hydrateMissingHistoryEntries(int familyDbId, int childId,
                                              List<HistoryEntryEntity> rows,
                                              Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> taskMap,
                                              Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopMap) {
        List<Long> missingTaskIds = rows.stream()
            .filter(entry -> entry.getType() == com.sashplatonov.earnit.kids.domain.model.HistoryEntryType.earn
                && entry.getRelatedId() != null)
            .map(HistoryEntryEntity::getRelatedId)
            .filter(relatedId -> !taskMap.containsKey(relatedId))
            .distinct()
            .toList();
        List<Long> missingShopIds = rows.stream()
            .filter(entry -> entry.getType() == com.sashplatonov.earnit.kids.domain.model.HistoryEntryType.spend
                && entry.getRelatedId() != null)
            .map(HistoryEntryEntity::getRelatedId)
            .filter(relatedId -> !shopMap.containsKey(relatedId))
            .distinct()
            .toList();

        if (!missingTaskIds.isEmpty()) {
            taskRepository.findByFamilyAndChildAndTaskIds(familyDbId, List.of(childId), missingTaskIds).stream()
                .map(task -> mapper.toTaskDto(task, null, objectMapper))
                .forEach(task -> taskMap.putIfAbsent(task.id(), task));
        }
        if (!missingShopIds.isEmpty()) {
            shopItemRepository.findByFamilyAndChildAndItemIds(familyDbId, List.of(childId), missingShopIds).stream()
                .map(item -> mapper.toShopItemDto(item, null, objectMapper))
                .forEach(item -> shopMap.putIfAbsent(item.id(), item));
        }
    }

    private void hydrateMissingRequests(int familyDbId, List<PurchaseRequestEntity> rows,
                                        Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> taskMap,
                                        Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopMap) {
        List<Integer> childIds = rows.stream()
            .map(PurchaseRequestEntity::getChildId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        List<Long> missingTaskIds = rows.stream()
            .map(PurchaseRequestEntity::getTaskId)
            .filter(Objects::nonNull)
            .filter(taskId -> !taskMap.containsKey(taskId))
            .distinct()
            .toList();
        List<Long> missingShopIds = rows.stream()
            .map(PurchaseRequestEntity::getItemId)
            .filter(Objects::nonNull)
            .filter(itemId -> !shopMap.containsKey(itemId))
            .distinct()
            .toList();

        if (!missingTaskIds.isEmpty() && !childIds.isEmpty()) {
            taskRepository.findByFamilyAndChildAndTaskIds(familyDbId, childIds, missingTaskIds).stream()
                .map(task -> mapper.toTaskDto(task, null, objectMapper))
                .forEach(task -> taskMap.putIfAbsent(task.id(), task));
        }
        if (!missingShopIds.isEmpty() && !childIds.isEmpty()) {
            shopItemRepository.findByFamilyAndChildAndItemIds(familyDbId, childIds, missingShopIds).stream()
                .map(item -> mapper.toShopItemDto(item, null, objectMapper))
                .forEach(item -> shopMap.putIfAbsent(item.id(), item));
        }
    }

    private HistoryEntryDto toHistoryDto(HistoryEntryEntity entry,
                                         Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> taskMap,
                                         Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopMap) {
        FamilyRelatedDetailsResolver.HistoryDetails details =
            FamilyRelatedDetailsResolver.resolveHistoryDetails(entry, taskMap, shopMap, mapper);
        return new HistoryEntryDto(
            entry.getExternalId(),
            entry.getType(),
            entry.getAmount(),
            details.title(),
            details.description(),
            entry.getMoneyAmount(),
            entry.getRelatedId(),
            details.taskId(),
            details.taskName(),
            details.itemId(),
            details.itemName(),
            details.groupName(),
            details.comment(),
            entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null,
            entry.getChildId()
        );
    }

    private RequestDto toRequestDto(PurchaseRequestEntity request,
                                    Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> taskMap,
                                    Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopMap) {
        FamilyRelatedDetailsResolver.RequestDetails details =
            FamilyRelatedDetailsResolver.resolveRequestDetails(request, taskMap, shopMap, mapper);
        return new RequestDto(
            request.getId(),
            request.getTaskId(),
            details.taskName(),
            request.getItemId(),
            details.itemName(),
            details.title(),
            details.description(),
            details.groupName(),
            details.comment(),
            request.getNote(),
            request.getCoins(),
            request.getStatus(),
            request.getRequestType(),
            request.getMoneyAmount(),
            request.getCreatedAt() != null ? request.getCreatedAt().toString() : null,
            request.getChildId(),
            details.taskGroup(),
            details.itemGroup(),
            details.taskComment(),
            details.itemComment()
        );
    }
}
