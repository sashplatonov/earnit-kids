package com.sashplatonov.earnit.kids.service.family.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.dto.response.TaskPeriodProgressDto;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.service.family.action.FrequencyWindowService;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyDashboardCatalogLoader {
    private final HistoryRepository historyRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final FamilyRepository familyRepository;
    private final TimeProvider timeProvider;
    private final FrequencyWindowService frequencyWindowService;
    private final FamilyDashboardMapper mapper;
    private final ObjectMapper objectMapper;

    FamilyDashboardCatalogContext loadCatalogContext(int familyDbId, int childId) {
        Map<Long, String> lastCompletedAtByTaskId = loadLatestHistoryTimestamps(childId, HistoryEntryType.earn);
        Map<Long, String> lastPurchasedAtByItemId = loadLatestHistoryTimestamps(childId, HistoryEntryType.spend);
        ZoneId zoneId = resolveZone(familyDbId);
        List<TaskDto> tasks = loadTasks(
            familyDbId, childId, lastCompletedAtByTaskId, zoneId
        );
        List<ShopItemDto> shopItems = loadShopItems(familyDbId, childId, lastPurchasedAtByItemId, zoneId);
        Map<Long, TaskDto> taskMap = buildTaskMap(tasks);
        Map<Long, ShopItemDto> shopMap = buildShopItemMap(shopItems);
        return new FamilyDashboardCatalogContext(tasks, shopItems, taskMap, shopMap);
    }

    private List<TaskDto> loadTasks(int familyDbId,
                                    int childId,
                                    Map<Long, String> lastCompletedAtByTaskId,
                                    ZoneId zoneId) {
        var taskEntities = taskRepository.getTasks(childId);
        Instant now = timeProvider.now();
        Map<Long, TaskPeriodProgressDto> progressByTaskId = new HashMap<>();
        Map<String, Map<Long, Long>> completedByPeriod = new HashMap<>();
        Map<String, Map<Long, Long>> pendingByPeriod = new HashMap<>();
        for (var task : taskEntities) {
            var resolvedWindow = frequencyWindowService.resolveCurrentWindow(task.getFrequency(), now, zoneId);
            if (resolvedWindow.isEmpty()) {
                continue;
            }
            var window = resolvedWindow.get();
            long completed = completedByPeriod.computeIfAbsent(window.period(), ignored ->
                historyRepository.countTaskEarnsInWindowByTask(familyDbId, childId, window.start(), window.end())
            ).getOrDefault(task.getTaskId(), 0L);
            long pending = pendingByPeriod.computeIfAbsent(window.period(), ignored ->
                purchaseRequestRepository.countPendingTaskRequestsInWindowByTask(
                    familyDbId, childId, window.start(), window.end()
                )
            ).getOrDefault(task.getTaskId(), 0L);
            int used = Math.toIntExact(Math.min(Integer.MAX_VALUE, completed + pending));
            progressByTaskId.put(task.getTaskId(), new TaskPeriodProgressDto(
                window.period(),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, completed)),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, pending)),
                window.limit(),
                Math.max(0, window.limit() - used),
                used < window.limit() && task.isActive(),
                window.start(),
                window.end()
            ));
        }
        return taskEntities.stream()
            .map(task -> mapper.toTaskDto(
                task,
                lastCompletedAtByTaskId.get(task.getTaskId()),
                progressByTaskId.get(task.getTaskId()),
                objectMapper
            ))
            .toList();
    }

    private ZoneId resolveZone(int familyDbId) {
        try {
            return ZoneId.of(familyRepository.getTimezone(familyDbId).orElse("UTC"));
        } catch (DateTimeException ignored) {
            return ZoneId.of("UTC");
        }
    }

    private List<ShopItemDto> loadShopItems(int familyDbId,
                                            int childId,
                                            Map<Long, String> lastPurchasedAtByItemId,
                                            ZoneId zoneId) {
        var shopItemEntities = shopItemRepository.getShopItems(childId);
        Instant now = timeProvider.now();
        Map<Long, TaskPeriodProgressDto> progressByItemId = new HashMap<>();
        Map<String, Map<Long, Long>> purchasedByPeriod = new HashMap<>();
        Map<String, Map<Long, Long>> pendingByPeriod = new HashMap<>();
        for (var shopItem : shopItemEntities) {
            var resolvedWindow = frequencyWindowService.resolveCurrentWindow(shopItem.getFrequency(), now, zoneId);
            if (resolvedWindow.isEmpty()) {
                continue;
            }
            var window = resolvedWindow.get();
            long purchased = purchasedByPeriod.computeIfAbsent(window.period(), ignored ->
                historyRepository.countShopPurchasesInWindowByItem(familyDbId, childId, window.start(), window.end())
            ).getOrDefault(shopItem.getItemId(), 0L);
            long pending = pendingByPeriod.computeIfAbsent(window.period(), ignored ->
                purchaseRequestRepository.countPendingItemRequestsInWindowByItem(
                    familyDbId, childId, window.start(), window.end()
                )
            ).getOrDefault(shopItem.getItemId(), 0L);
            int used = Math.toIntExact(Math.min(Integer.MAX_VALUE, purchased + pending));
            progressByItemId.put(shopItem.getItemId(), new TaskPeriodProgressDto(
                window.period(),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, purchased)),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, pending)),
                window.limit(),
                Math.max(0, window.limit() - used),
                used < window.limit() && shopItem.isActive(),
                window.start(),
                window.end()
            ));
        }
        return shopItemEntities.stream()
            .map(shopItem -> mapper.toShopItemDto(
                shopItem,
                lastPurchasedAtByItemId.get(shopItem.getItemId()),
                progressByItemId.get(shopItem.getItemId()),
                objectMapper
            ))
            .toList();
    }

    private Map<Long, TaskDto> buildTaskMap(List<TaskDto> tasks) {
        Map<Long, TaskDto> taskMap = new LinkedHashMap<>();
        for (var task : tasks) {
            taskMap.putIfAbsent(task.id(), task);
        }
        return taskMap;
    }

    private Map<Long, ShopItemDto> buildShopItemMap(List<ShopItemDto> shopItems) {
        Map<Long, ShopItemDto> shopMap = new LinkedHashMap<>();
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
