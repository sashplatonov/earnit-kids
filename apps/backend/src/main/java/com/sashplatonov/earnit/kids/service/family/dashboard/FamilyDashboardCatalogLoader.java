package com.sashplatonov.earnit.kids.service.family.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyDashboardCatalogLoader {
    private final HistoryRepository historyRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final FamilyDashboardMapper mapper;
    private final ObjectMapper objectMapper;

    FamilyDashboardCatalogContext loadCatalogContext(int childId) {
        Map<Long, String> lastCompletedAtByTaskId = loadLatestHistoryTimestamps(childId, HistoryEntryType.earn);
        Map<Long, String> lastPurchasedAtByItemId = loadLatestHistoryTimestamps(childId, HistoryEntryType.spend);
        List<com.sashplatonov.earnit.kids.dto.response.TaskDto> tasks = loadTasks(childId, lastCompletedAtByTaskId);
        List<com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopItems = loadShopItems(childId, lastPurchasedAtByItemId);
        Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> taskMap = buildTaskMap(tasks);
        Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopMap = buildShopItemMap(shopItems);
        return new FamilyDashboardCatalogContext(tasks, shopItems, taskMap, shopMap);
    }

    private List<com.sashplatonov.earnit.kids.dto.response.TaskDto> loadTasks(int childId,
                                                                              Map<Long, String> lastCompletedAtByTaskId) {
        return taskRepository.getTasks(childId).stream()
            .map(task -> mapper.toTaskDto(task, lastCompletedAtByTaskId.get(task.getTaskId()), objectMapper))
            .toList();
    }

    private List<com.sashplatonov.earnit.kids.dto.response.ShopItemDto> loadShopItems(int childId,
                                                                                      Map<Long, String> lastPurchasedAtByItemId) {
        return shopItemRepository.getShopItems(childId).stream()
            .map(shopItem -> mapper.toShopItemDto(shopItem, lastPurchasedAtByItemId.get(shopItem.getItemId()), objectMapper))
            .toList();
    }

    private Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> buildTaskMap(
        List<com.sashplatonov.earnit.kids.dto.response.TaskDto> tasks) {
        Map<Long, com.sashplatonov.earnit.kids.dto.response.TaskDto> taskMap = new LinkedHashMap<>();
        for (var task : tasks) {
            taskMap.putIfAbsent(task.id(), task);
        }
        return taskMap;
    }

    private Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> buildShopItemMap(
        List<com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopItems) {
        Map<Long, com.sashplatonov.earnit.kids.dto.response.ShopItemDto> shopMap = new LinkedHashMap<>();
        for (var shopItem : shopItems) {
            shopMap.putIfAbsent(shopItem.id(), shopItem);
        }
        return shopMap;
    }

    private Map<Long, String> loadLatestHistoryTimestamps(int childId, HistoryEntryType type) {
        Map<Long, Instant> aggregated = historyRepository.loadLatestTimestampsByRelatedId(childId, type);
        Map<Long, String> latestTimestamps = new LinkedHashMap<>();
        if (aggregated != null && !aggregated.isEmpty()) {
            aggregated.forEach((id, instant) -> latestTimestamps.put(id, instant.toString()));
            return latestTimestamps;
        }

        historyRepository.list(
            "childId = ?1 AND type = ?2 AND relatedId IS NOT NULL ORDER BY createdAt DESC, id DESC",
            childId,
            type
        ).stream()
            .filter(entry -> entry.getRelatedId() != null && entry.getCreatedAt() != null)
            .forEach(entry -> latestTimestamps.putIfAbsent(entry.getRelatedId(), entry.getCreatedAt().toString()));
        return latestTimestamps;
    }
}
