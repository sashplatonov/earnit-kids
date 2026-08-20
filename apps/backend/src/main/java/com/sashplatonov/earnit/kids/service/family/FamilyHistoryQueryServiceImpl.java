package com.sashplatonov.earnit.kids.service.family;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.service.common.ServiceResults;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardMapper;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public final class FamilyHistoryQueryServiceImpl implements FamilyHistoryQueryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ChildRepository childRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final HistoryRepository historyRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final FamilyDashboardMapper mapper;
    private final ObjectMapper objectMapper;
    private final FamilyOperationGuard familyOperationGuard;
    private final ChildOwnershipService childOwnershipService;
    private final HistoryDtoMapper historyDtoMapper;
    private final RelatedEntityHydrator relatedEntityHydrator;

    @Inject
    public FamilyHistoryQueryServiceImpl(ChildRepository childRepository,
                                         TaskRepository taskRepository,
                                         ShopItemRepository shopItemRepository,
                                         HistoryRepository historyRepository,
                                         PurchaseRequestRepository purchaseRequestRepository,
                                         FamilyDashboardMapper mapper,
                                         ObjectMapper objectMapper,
                                         FamilyOperationGuard familyOperationGuard,
                                         ChildOwnershipService childOwnershipService,
                                         HistoryDtoMapper historyDtoMapper,
                                         RelatedEntityHydrator relatedEntityHydrator) {
        this.childRepository = childRepository;
        this.taskRepository = taskRepository;
        this.shopItemRepository = shopItemRepository;
        this.historyRepository = historyRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.familyOperationGuard = familyOperationGuard;
        this.childOwnershipService = childOwnershipService;
        this.historyDtoMapper = historyDtoMapper;
        this.relatedEntityHydrator = relatedEntityHydrator;
    }

    @Override
    public OperationResult<PaginatedHistory> getHistory(String familyId, int childId, int page, int limit) {
        OperationResult<Integer> familyDbIdResult = familyOperationGuard.requireFamilyDbId(familyId);
        if (familyDbIdResult.isFailure()) {
            return familyDbIdResult.asFailure();
        }
        int familyDbId = ((OperationResult.Success<Integer>) familyDbIdResult).value();
        if (findFamilyChild(familyDbId, childId).isEmpty()) {
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int offset = (page - 1) * effectiveLimit;
        List<HistoryEntryEntity> rows = historyRepository.getHistory(childId, effectiveLimit, offset);
        int total = historyRepository.getHistoryCount(childId);
        List<TaskDto> tasks = loadTasks(childId, Map.of());
        List<ShopItemDto> shopItems = loadShopItems(childId, Map.of());
        Map<Long, TaskDto> taskMap = buildTaskMap(tasks);
        Map<Long, ShopItemDto> shopMap = buildShopItemMap(shopItems);
        relatedEntityHydrator.hydrateMissingHistoryEntries(familyDbId, childId, rows, taskMap, shopMap);
        List<HistoryEntryDto> items = rows.stream()
            .map(historyEntry -> historyDtoMapper.toDto(historyEntry, taskMap, shopMap))
            .toList();
        return OperationResult.success(new PaginatedHistory(items, total, page, effectiveLimit));
    }

    @Override
    public OperationResult<PaginatedRequests> getRequests(String familyId, int page, int limit) {
        OperationResult<Integer> familyDbIdResult = familyOperationGuard.requireFamilyDbId(familyId);
        if (familyDbIdResult.isFailure()) {
            return familyDbIdResult.asFailure();
        }
        int familyDbId = ((OperationResult.Success<Integer>) familyDbIdResult).value();
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int offset = (page - 1) * effectiveLimit;
        List<PurchaseRequestEntity> rows = purchaseRequestRepository.getRequests(familyDbId, effectiveLimit, offset);
        int total = purchaseRequestRepository.getRequestsCount(familyDbId);
        Map<Long, TaskDto> taskMap = new LinkedHashMap<>();
        Map<Long, ShopItemDto> shopMap = new LinkedHashMap<>();
        rows.stream()
            .map(PurchaseRequestEntity::getChildId)
            .filter(Objects::nonNull)
            .distinct()
            .forEach(requestChildId -> {
                loadTasks(requestChildId, Map.of()).forEach(task -> taskMap.putIfAbsent(task.id(), task));
                loadShopItems(requestChildId, Map.of())
                    .forEach(shopItem -> shopMap.putIfAbsent(shopItem.id(), shopItem));
            });
        relatedEntityHydrator.hydrateMissingRequests(familyDbId, rows, taskMap, shopMap);
        List<RequestDto> items = rows.stream().map(request -> toRequestDto(request, taskMap, shopMap)).toList();
        return OperationResult.success(new PaginatedRequests(items, total, page, effectiveLimit));
    }

    private Optional<ChildEntity> findFamilyChild(int familyDbId, int childId) {
        return childOwnershipService.findFamilyChild(familyDbId, childId);
    }

    private List<TaskDto> loadTasks(int childId, Map<Long, String> lastCompletedAtByTaskId) {
        return taskRepository.getTasks(childId).stream()
            .map(task -> mapper.toTaskDto(task, lastCompletedAtByTaskId.get(task.getTaskId()), objectMapper))
            .toList();
    }

    private List<ShopItemDto> loadShopItems(int childId, Map<Long, String> lastPurchasedAtByItemId) {
        return shopItemRepository.getShopItems(childId).stream()
            .map(shopItem -> mapper.toShopItemDto(shopItem, lastPurchasedAtByItemId.get(shopItem.getItemId()), objectMapper))
            .toList();
    }

    private Map<Long, TaskDto> buildTaskMap(List<TaskDto> tasks) {
        Map<Long, TaskDto> taskMap = new LinkedHashMap<>();
        for (TaskDto task : tasks) {
            taskMap.putIfAbsent(task.id(), task);
        }
        return taskMap;
    }

    private Map<Long, ShopItemDto> buildShopItemMap(List<ShopItemDto> shopItems) {
        Map<Long, ShopItemDto> shopMap = new LinkedHashMap<>();
        for (ShopItemDto shopItem : shopItems) {
            shopMap.putIfAbsent(shopItem.id(), shopItem);
        }
        return shopMap;
    }

    private RequestDto toRequestDto(PurchaseRequestEntity request,
                                    Map<Long, TaskDto> taskMap,
                                    Map<Long, ShopItemDto> shopMap) {
        FamilyRelatedDetailsResolver.RequestDetails details =
            FamilyRelatedDetailsResolver.resolveRequestDetails(request, taskMap, shopMap, mapper);
        return new RequestDto(request.getId(), request.getTaskId(), details.taskName(),
            request.getItemId(), details.itemName(), details.title(), details.description(),
            details.groupName(),
            details.comment(),
            request.getNote(),
            request.getCoins(),
            request.getStatus(),
            request.getRequestType(),
            request.getMoneyAmount(), request.getCreatedAt() != null ? request.getCreatedAt().toString() : null,
            request.getChildId(),
            details.taskGroup(),
            details.itemGroup(),
            details.taskComment(),
            details.itemComment()
        );
    }
}
