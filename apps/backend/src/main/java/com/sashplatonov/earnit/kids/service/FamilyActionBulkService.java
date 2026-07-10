package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.request.BulkActionType;
import com.sashplatonov.earnit.kids.dto.request.BulkShopItemActionRequest;
import com.sashplatonov.earnit.kids.dto.request.BulkTaskActionRequest;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

final class FamilyActionBulkService {

    private final FamilyActionSupportService supportService;

    FamilyActionBulkService(FamilyActionSupportService supportService) {
        this.supportService = supportService;
    }

    OperationResult<FamilyDataResponse> bulkTaskAction(String familyId, BulkTaskActionRequest request) {
        var familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }
        if (supportService.findFamilyChild(familyDbId.get(), request.childId()).isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        LinkedHashSet<Long> taskIds = normalizedIds(request.taskIds());
        if (taskIds.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("tasks.notFound"));
        }

        BulkActionType action = request.action();
        if (action == null) {
            return unknownBulkAction(null);
        }

        Map<Long, TaskEntity> tasksByBusinessId = selectedEntitiesByBusinessId(
            supportService.findTaskEntities(familyDbId.get(), request.childId()),
            taskIds,
            TaskEntity::getTaskId
        );
        if (tasksByBusinessId.size() != taskIds.size()) {
            return OperationResult.failure(BackendMessages.message("tasks.notFound"));
        }

        String actionError = applyBulkAction(
            action,
            request.groupName(),
            tasksByBusinessId.values(),
            BackendMessages.message("tasks.groupNameRequired"),
            task -> task.setDeleted(true),
            task -> task.setActive(false),
            task -> task.setActive(true),
            TaskEntity::setGroupName
        );
        if (actionError != null) {
            return OperationResult.failure(actionError);
        }

        return supportService.loadFamilyData(familyId, request.childId(), true);
    }

    OperationResult<FamilyDataResponse> bulkShopItemAction(String familyId, BulkShopItemActionRequest request) {
        var familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }
        if (supportService.findFamilyChild(familyDbId.get(), request.childId()).isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }

        LinkedHashSet<Long> itemIds = normalizedIds(request.itemIds());
        if (itemIds.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("shop.itemNotFound"));
        }

        BulkActionType action = request.action();
        if (action == null) {
            return unknownBulkAction(null);
        }

        Map<Long, ShopItemEntity> itemsByBusinessId = selectedEntitiesByBusinessId(
            supportService.findShopItemEntities(familyDbId.get(), request.childId()),
            itemIds,
            ShopItemEntity::getItemId
        );
        if (itemsByBusinessId.size() != itemIds.size()) {
            return OperationResult.failure(BackendMessages.message("shop.itemNotFound"));
        }

        String actionError = applyBulkAction(
            action,
            request.groupName(),
            itemsByBusinessId.values(),
            BackendMessages.message("shop.groupNameRequired"),
            item -> item.setDeleted(true),
            item -> item.setActive(false),
            item -> item.setActive(true),
            ShopItemEntity::setGroupName
        );
        if (actionError != null) {
            return OperationResult.failure(actionError);
        }

        return supportService.loadFamilyData(familyId, request.childId(), true);
    }

    private static LinkedHashSet<Long> normalizedIds(List<Long> rawIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : rawIds) {
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        return ids;
    }

    private OperationResult<FamilyDataResponse> unknownBulkAction(String action) {
        return OperationResult.failure(
            BackendMessages.message("family.unknownSetting", Map.of("key", String.valueOf(action)))
        );
    }

    private <T> Map<Long, T> selectedEntitiesByBusinessId(
        List<T> entities,
        LinkedHashSet<Long> requestedIds,
        Function<T, Long> businessIdExtractor
    ) {
        return entities.stream()
            .filter(entity -> requestedIds.contains(businessIdExtractor.apply(entity)))
            .collect(Collectors.toMap(
                businessIdExtractor,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private <T> String applyBulkAction(
        BulkActionType action,
        String rawGroupName,
        Iterable<T> entities,
        String groupNameRequiredMessage,
        Consumer<T> deleteAction,
        Consumer<T> blockAction,
        Consumer<T> unblockAction,
        BiConsumer<T, String> groupAction
    ) {
        switch (action) {
            case delete -> forEachEntity(entities, deleteAction);
            case block -> forEachEntity(entities, blockAction);
            case unblock -> forEachEntity(entities, unblockAction);
            case change_group -> {
                String groupName = trimToNull(rawGroupName);
                if (groupName == null) {
                    return groupNameRequiredMessage;
                }
                forEachEntity(entities, entity -> groupAction.accept(entity, groupName));
            }
        }
        return null;
    }

    private <T> void forEachEntity(Iterable<T> entities, Consumer<T> action) {
        for (T entity : entities) {
            action.accept(entity);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
