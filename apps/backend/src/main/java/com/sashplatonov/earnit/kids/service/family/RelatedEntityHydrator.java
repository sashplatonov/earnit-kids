package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@ApplicationScoped
public class RelatedEntityHydrator {

    private final Supplier<TaskRepository> taskRepository;
    private final Supplier<ShopItemRepository> shopItemRepository;
    private final FamilyDashboardMapper mapper;
    private final Supplier<com.fasterxml.jackson.databind.ObjectMapper> objectMapper;

    public RelatedEntityHydrator(TaskRepository taskRepository,
                                 ShopItemRepository shopItemRepository,
                                 FamilyDashboardMapper mapper,
                                 com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.taskRepository = () -> taskRepository;
        this.shopItemRepository = () -> shopItemRepository;
        this.mapper = mapper;
        this.objectMapper = () -> objectMapper;
    }

    public void hydrateMissingHistoryEntries(int familyDbId, int childId, List<HistoryEntryEntity> rows,
                                             Map<Long, TaskDto> taskMap, Map<Long, ShopItemDto> shopMap) {
        List<Long> missingTaskIds = rows.stream()
            .filter(entry -> entry.getType() == HistoryEntryType.earn && entry.getRelatedId() != null)
            .map(HistoryEntryEntity::getRelatedId)
            .filter(relatedId -> !taskMap.containsKey(relatedId))
            .distinct()
            .toList();
        List<Long> missingShopIds = rows.stream()
            .filter(entry -> entry.getType() == HistoryEntryType.spend && entry.getRelatedId() != null)
            .map(HistoryEntryEntity::getRelatedId)
            .filter(relatedId -> !shopMap.containsKey(relatedId))
            .distinct()
            .toList();

        if (!missingTaskIds.isEmpty()) {
            taskRepository.get().findByFamilyAndChildAndTaskIds(familyDbId, List.of(childId), missingTaskIds).stream()
                .map(task -> mapper.toTaskDto(task, null, objectMapper.get()))
                .forEach(task -> taskMap.putIfAbsent(task.id(), task));
        }
        if (!missingShopIds.isEmpty()) {
            shopItemRepository.get().findByFamilyAndChildAndItemIds(familyDbId, List.of(childId), missingShopIds).stream()
                .map(item -> mapper.toShopItemDto(item, null, objectMapper.get()))
                .forEach(item -> shopMap.putIfAbsent(item.id(), item));
        }
    }

    public void hydrateMissingRequests(int familyDbId, List<PurchaseRequestEntity> rows,
                                       Map<Long, TaskDto> taskMap, Map<Long, ShopItemDto> shopMap) {
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
            taskRepository.get().findByFamilyAndChildAndTaskIds(familyDbId, childIds, missingTaskIds).stream()
                .map(task -> mapper.toTaskDto(task, null, objectMapper.get()))
                .forEach(task -> taskMap.putIfAbsent(task.id(), task));
        }
        if (!missingShopIds.isEmpty() && !childIds.isEmpty()) {
            shopItemRepository.get().findByFamilyAndChildAndItemIds(familyDbId, childIds, missingShopIds).stream()
                .map(item -> mapper.toShopItemDto(item, null, objectMapper.get()))
                .forEach(item -> shopMap.putIfAbsent(item.id(), item));
        }
    }
}
