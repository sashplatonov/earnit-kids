package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.ChildDto;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyDataRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyDashboardQueryServiceImpl implements FamilyDashboardQueryService {

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final FamilyDataRepository familyDataRepository;
    private final HistoryRepository historyRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final ObjectMapper objectMapper;
    private final BackendKpiMetrics backendKpiMetrics;

    @Override
    public OperationResult<FamilyDashboardShellResponse> loadFamilyShellData(String familyId, Integer childId,
                                                                             boolean adminSession) {
        return backendKpiMetrics.recordResult("dashboard", "shell", () -> {
            Optional<DashboardSnapshot> snapshotOpt = loadSnapshot(familyId, childId, adminSession);
            if (snapshotOpt.isEmpty()) {
                return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
            }

            DashboardSnapshot snapshot = snapshotOpt.get();
            if (snapshot.activeChild == null) {
                return OperationResult.success(emptyShellResponse(snapshot.rules, adminSession));
            }

            return OperationResult.success(new FamilyDashboardShellResponse(
                snapshot.activeChild.getBalance(),
                snapshot.rules,
                snapshot.tasks,
                snapshot.shopItems,
                adminSession ? Boolean.TRUE : null,
                snapshot.visibleChildren.stream().map(this::toChildDto).toList(),
                snapshot.resolvedLastSelectedChildId,
                snapshot.activeChild.getId(),
                snapshot.activeChild.getName(),
                snapshot.activeChild.getMonthlyLimit(),
                snapshot.activeChild.getDailyCoinLimit()
            ));
        });
    }

    @Override
    public OperationResult<FamilyDashboardDetailResponse> loadFamilyDetailData(String familyId, Integer childId,
                                                                              boolean adminSession) {
        return backendKpiMetrics.recordResult("dashboard", "detail", () -> {
            Optional<DashboardSnapshot> snapshotOpt = loadSnapshot(familyId, childId, adminSession);
            if (snapshotOpt.isEmpty()) {
                return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
            }

            DashboardSnapshot snapshot = snapshotOpt.get();
            if (snapshot.activeChild == null) {
                return OperationResult.success(new FamilyDashboardDetailResponse(List.of(), List.of(), List.of()));
            }

            return OperationResult.success(new FamilyDashboardDetailResponse(
                loadHistory(snapshot.familyDbId, snapshot.activeChild.getId(), snapshot.taskMap, snapshot.shopMap),
                loadRequests(
                    snapshot.familyDbId,
                    snapshot.activeChild.getId(),
                    adminSession,
                    snapshot.taskMap,
                    snapshot.shopMap
                ),
                loadFriends(snapshot.activeChild.getId())
            ));
        });
    }

    @Override
    public OperationResult<FamilyDataResponse> loadFamilyData(String familyId, Integer childId, boolean adminSession) {
        return backendKpiMetrics.recordResult("dashboard", "full", () -> {
            Optional<DashboardSnapshot> snapshotOpt = loadSnapshot(familyId, childId, adminSession);
            if (snapshotOpt.isEmpty()) {
                return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
            }

            DashboardSnapshot snapshot = snapshotOpt.get();
            if (snapshot.activeChild == null) {
                return OperationResult.success(emptyFamilyDataResponse(snapshot.rules, adminSession));
            }

            return OperationResult.success(new FamilyDataResponse(
                snapshot.activeChild.getBalance(),
                snapshot.rules,
                snapshot.tasks,
                snapshot.shopItems,
                loadHistory(snapshot.familyDbId, snapshot.activeChild.getId(), snapshot.taskMap, snapshot.shopMap),
                loadRequests(
                    snapshot.familyDbId,
                    snapshot.activeChild.getId(),
                    adminSession,
                    snapshot.taskMap,
                    snapshot.shopMap
                ),
                loadFriends(snapshot.activeChild.getId()),
                adminSession ? Boolean.TRUE : null,
                snapshot.visibleChildren.stream().map(this::toChildDto).toList(),
                snapshot.resolvedLastSelectedChildId,
                snapshot.activeChild.getName(),
                snapshot.activeChild.getMonthlyLimit(),
                snapshot.activeChild.getDailyCoinLimit()
            ));
        });
    }

    private Optional<DashboardSnapshot> loadSnapshot(String familyId, Integer childId, boolean adminSession) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return Optional.empty();
        }

        int familyDbId = dbIdOpt.get();
        String rules = familyRepository.getRules(familyId).orElse(null);
        Integer persistedChildId = familyRepository.getLastSelectedChildId(familyId).orElse(null);
        List<ChildEntity> children = childRepository.getChildren(familyDbId);

        if (children.isEmpty()) {
            return Optional.of(DashboardSnapshot.empty(familyDbId, rules));
        }

        List<ChildEntity> visibleChildren = resolveVisibleChildren(children, adminSession, childId);
        if (visibleChildren.isEmpty()) {
            return Optional.empty();
        }

        ChildEntity activeChild = resolveActiveChild(visibleChildren, childId, persistedChildId, adminSession);
        Integer resolvedLastSelectedChildId = resolveLastSelectedChildId(
            children,
            activeChild,
            persistedChildId,
            adminSession
        );
        Map<Long, String> lastCompletedAtByTaskId =
            loadLatestHistoryTimestamps(activeChild.getId(), HistoryEntryType.earn);
        Map<Long, String> lastPurchasedAtByItemId =
            loadLatestHistoryTimestamps(activeChild.getId(), HistoryEntryType.spend);
        List<TaskDto> tasks = loadTasks(activeChild.getId(), lastCompletedAtByTaskId);
        List<ShopItemDto> shopItems = loadShopItems(activeChild.getId(), lastPurchasedAtByItemId);
        Map<Long, TaskDto> taskMap = tasks.stream()
            .collect(java.util.stream.Collectors.toMap(TaskDto::id, task -> task, (left, right) -> left));
        Map<Long, ShopItemDto> shopMap = shopItems.stream()
            .collect(java.util.stream.Collectors.toMap(ShopItemDto::id, item -> item, (left, right) -> left));

        return Optional.of(new DashboardSnapshot(
            familyDbId,
            rules,
            activeChild,
            visibleChildren,
            resolvedLastSelectedChildId,
            tasks,
            shopItems,
            taskMap,
            shopMap
        ));
    }

    private FamilyDataResponse emptyFamilyDataResponse(String rules, boolean adminSession) {
        return new FamilyDataResponse(
            0,
            rules,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            adminSession ? Boolean.TRUE : null,
            List.of(),
            null,
            null,
            null,
            null
        );
    }

    private FamilyDashboardShellResponse emptyShellResponse(String rules, boolean adminSession) {
        return new FamilyDashboardShellResponse(
            0,
            rules,
            List.of(),
            List.of(),
            adminSession ? Boolean.TRUE : null,
            List.of(),
            null,
            null,
            null,
            null,
            null
        );
    }

    private List<ChildEntity> resolveVisibleChildren(List<ChildEntity> children,
                                                     boolean adminSession,
                                                     Integer childId) {
        if (adminSession) {
            return children;
        }
        if (childId == null) {
            return List.of();
        }
        return children.stream()
            .filter(child -> Objects.equals(child.getId(), childId))
            .toList();
    }

    private ChildEntity resolveActiveChild(List<ChildEntity> visibleChildren,
                                           Integer requestedChildId,
                                           Integer persistedChildId,
                                           boolean adminSession) {
        Integer preferredChildId = adminSession
            ? (requestedChildId != null ? requestedChildId : persistedChildId)
            : visibleChildren.getFirst().getId();
        if (preferredChildId == null) {
            return visibleChildren.getFirst();
        }
        return visibleChildren.stream()
            .filter(child -> Objects.equals(child.getId(), preferredChildId))
            .findFirst()
            .orElse(visibleChildren.getFirst());
    }

    private Integer resolveLastSelectedChildId(List<ChildEntity> children,
                                               ChildEntity activeChild,
                                               Integer persistedChildId,
                                               boolean adminSession) {
        if (!adminSession) {
            return activeChild.getId();
        }
        return children.stream()
            .map(ChildEntity::getId)
            .filter(id -> Objects.equals(id, persistedChildId))
            .findFirst()
            .orElse(activeChild.getId());
    }

    private List<HistoryEntryDto> loadHistory(int familyDbId, int childId,
                                              Map<Long, TaskDto> taskMap, Map<Long, ShopItemDto> shopMap) {
        List<HistoryEntryEntity> rows = familyDataRepository.getHistory(childId, 50, 0);
        hydrateMissingHistoryEntries(familyDbId, childId, rows, taskMap, shopMap);
        return rows.stream()
            .map(historyEntry -> toHistoryDto(historyEntry, taskMap, shopMap))
            .toList();
    }

    private List<RequestDto> loadRequests(int familyDbId,
                                          int activeChildId,
                                          boolean adminSession,
                                          Map<Long, TaskDto> taskMap,
                                          Map<Long, ShopItemDto> shopMap) {
        List<PurchaseRequestEntity> rows = familyDataRepository.getRequests(familyDbId, 50, 0);
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

    private List<FriendDto> loadFriends(int childId) {
        var friendIds = familyDataRepository.getFriendChildIds(childId);
        return childRepository.findByChildIds(friendIds).stream()
            .map(friend -> new FriendDto(friend.getId(), friend.getName(), friend.getBalance()))
            .toList();
    }

    private List<TaskDto> loadTasks(int childId, Map<Long, String> lastCompletedAtByTaskId) {
        return familyDataRepository.getTasks(childId).stream()
            .map(task -> toTaskDto(task, lastCompletedAtByTaskId.get(task.getTaskId())))
            .toList();
    }

    private List<ShopItemDto> loadShopItems(int childId, Map<Long, String> lastPurchasedAtByItemId) {
        return familyDataRepository.getShopItems(childId).stream()
            .map(shopItem -> toShopItemDto(shopItem, lastPurchasedAtByItemId.get(shopItem.getItemId())))
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

    private void hydrateMissingHistoryEntries(int familyDbId, int childId, List<HistoryEntryEntity> rows,
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
            taskRepository.findByFamilyAndChildAndTaskIds(familyDbId, List.of(childId), missingTaskIds).stream()
                .map(task -> toTaskDto(task, null))
                .forEach(task -> taskMap.putIfAbsent(task.id(), task));
        }
        if (!missingShopIds.isEmpty()) {
            shopItemRepository.findByFamilyAndChildAndItemIds(familyDbId, List.of(childId), missingShopIds).stream()
                .map(item -> toShopItemDto(item, null))
                .forEach(item -> shopMap.putIfAbsent(item.id(), item));
        }
    }

    private void hydrateMissingRequests(int familyDbId, List<PurchaseRequestEntity> rows,
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
            taskRepository.findByFamilyAndChildAndTaskIds(familyDbId, childIds, missingTaskIds).stream()
                .map(task -> toTaskDto(task, null))
                .forEach(task -> taskMap.putIfAbsent(task.id(), task));
        }
        if (!missingShopIds.isEmpty() && !childIds.isEmpty()) {
            shopItemRepository.findByFamilyAndChildAndItemIds(familyDbId, childIds, missingShopIds).stream()
                .map(item -> toShopItemDto(item, null))
                .forEach(item -> shopMap.putIfAbsent(item.id(), item));
        }
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

    private ChildDto toChildDto(ChildEntity child) {
        return new ChildDto(
            child.getId(),
            child.getName(),
            child.getBalance(),
            child.getMonthlyLimit(),
            child.getDailyCoinLimit(),
            child.getTheme(),
            parseGroupOrder(child.getTaskGroupOrder()),
            parseGroupOrder(child.getShopGroupOrder()),
            parseGroupOrder(child.getChildTaskGroupOrder()),
            parseGroupOrder(child.getChildShopGroupOrder())
        );
    }

    private TaskDto toTaskDto(TaskEntity task, String lastCompletedAt) {
        return new TaskDto(
            task.getTaskId(),
            task.getName(),
            task.getCoins(),
            task.getGroupName(),
            parseFrequency(task.getFrequency()),
            task.getComment(),
            task.getMoneyLimit(),
            task.isActive(),
            task.getChildId(),
            lastCompletedAt
        );
    }

    private ShopItemDto toShopItemDto(ShopItemEntity shopItem, String lastPurchasedAt) {
        return new ShopItemDto(
            shopItem.getItemId(),
            shopItem.getName(),
            shopItem.getPrice(),
            shopItem.getGroupName(),
            parseFrequency(shopItem.getFrequency()),
            shopItem.getComment(),
            shopItem.getMoneyLimit(),
            shopItem.isActive(),
            shopItem.getChildId(),
            lastPurchasedAt
        );
    }

    private List<String> parseGroupOrder(String rawGroupOrder) {
        if (rawGroupOrder == null || rawGroupOrder.isBlank()) {
            return List.of();
        }

        try {
            JsonNode node = objectMapper.readTree(rawGroupOrder);
            if (!node.isArray()) {
                return List.of();
            }

            List<String> groups = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isTextual()) {
                    continue;
                }

                String value = item.asText().trim();
                if (!value.isEmpty() && !groups.contains(value)) {
                    groups.add(value);
                }
            }

            return List.copyOf(groups);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Object parseFrequency(JsonNode rawFrequency) {
        if (rawFrequency == null || rawFrequency.isNull()) {
            return null;
        }

        if (rawFrequency.isTextual()) {
            String value = rawFrequency.asText();
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readValue(value, Object.class);
            } catch (Exception ex) {
                return value;
            }
        }

        return objectMapper.convertValue(rawFrequency, Object.class);
    }

    private HistoryEntryDto toHistoryDto(HistoryEntryEntity entry,
                                         Map<Long, TaskDto> taskMap,
                                         Map<Long, ShopItemDto> shopMap) {
        HistoryDetails details = enrichHistoryDetails(entry, taskMap, shopMap);
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

    private HistoryDetails enrichHistoryDetails(HistoryEntryEntity entry,
                                                Map<Long, TaskDto> taskMap,
                                                Map<Long, ShopItemDto> shopMap) {
        if (entry.getRelatedId() == null) {
            return new HistoryDetails(entry.getDescription(), entry.getDescription(), null, null, null, null,
                entry.getGroupName(), entry.getComment());
        }

        if (entry.getType() == HistoryEntryType.earn) {
            TaskDto task = taskMap.get(entry.getRelatedId());
            if (task != null) {
                String title = firstNonBlank(entry.getDescription(), task.name());
                return new HistoryDetails(
                    title,
                    title,
                    task.id(),
                    task.name(),
                    null,
                    null,
                    firstNonBlank(entry.getGroupName(), task.groupName()),
                    firstNonBlank(entry.getComment(), task.comment())
                );
            }
        }

        if (entry.getType() == HistoryEntryType.spend) {
            ShopItemDto shopItem = shopMap.get(entry.getRelatedId());
            if (shopItem != null) {
                String title = firstNonBlank(entry.getDescription(), shopItem.name());
                return new HistoryDetails(
                    title,
                    title,
                    null,
                    null,
                    shopItem.id(),
                    shopItem.name(),
                    firstNonBlank(entry.getGroupName(), shopItem.groupName()),
                    firstNonBlank(entry.getComment(), shopItem.comment())
                );
            }
        }

        return new HistoryDetails(entry.getDescription(), entry.getDescription(), null, null, null, null,
            entry.getGroupName(), entry.getComment());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private RequestDto toRequestDto(PurchaseRequestEntity request,
                                    Map<Long, TaskDto> taskMap,
                                    Map<Long, ShopItemDto> shopMap) {
        RequestDetails details = enrichRequestDetails(request, taskMap, shopMap);
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

    private RequestDetails enrichRequestDetails(PurchaseRequestEntity request,
                                                Map<Long, TaskDto> taskMap,
                                                Map<Long, ShopItemDto> shopMap) {
        boolean purchase = isPurchaseRequest(request) || request.getItemId() != null;
        Long itemId = request.getItemId() != null ? request.getItemId() : request.getTaskId();
        ShopItemDto shopItem = null;
        TaskDto task = null;

        if (purchase && itemId != null) {
            shopItem = shopMap.get(itemId);
        } else if (request.getTaskId() != null) {
            task = taskMap.get(request.getTaskId());
        }

        String taskName = firstNonBlank(request.getTaskName(), task != null ? task.name() : null);
        String itemName = purchase
            ? firstNonBlank(shopItem != null ? shopItem.name() : null, request.getTaskName())
            : null;
        String title = purchase ? firstNonBlank(itemName, taskName) : taskName;
        String taskComment = task != null ? task.comment() : null;
        String itemComment = shopItem != null ? shopItem.comment() : null;
        String taskGroup = task != null ? task.groupName() : null;
        String itemGroup = shopItem != null ? shopItem.groupName() : null;
        String description = purchase ? itemComment : taskComment;
        String groupName = purchase ? itemGroup : taskGroup;

        return new RequestDetails(title, description, groupName, description, taskName, itemName,
            taskGroup, itemGroup, taskComment, itemComment);
    }

    private boolean isPurchaseRequest(PurchaseRequestEntity request) {
        return (request.getRequestType() != null && request.getRequestType().isPurchase())
            || request.getItemId() != null;
    }

    private static <T> OperationResult<T> failure(String errorCode, String messageKey) {
        return OperationResult.failure(errorCode, BackendMessages.message(messageKey));
    }

    private record DashboardSnapshot(
        int familyDbId,
        String rules,
        ChildEntity activeChild,
        List<ChildEntity> visibleChildren,
        Integer resolvedLastSelectedChildId,
        List<TaskDto> tasks,
        List<ShopItemDto> shopItems,
        Map<Long, TaskDto> taskMap,
        Map<Long, ShopItemDto> shopMap
    ) {
        private static DashboardSnapshot empty(int familyDbId, String rules) {
            return new DashboardSnapshot(
                familyDbId,
                rules,
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                Map.of(),
                Map.of()
            );
        }
    }

    private record HistoryDetails(
        String title,
        String description,
        Long taskId,
        String taskName,
        Long itemId,
        String itemName,
        String groupName,
        String comment
    ) { }

    private record RequestDetails(
        String title,
        String description,
        String groupName,
        String comment,
        String taskName,
        String itemName,
        String taskGroup,
        String itemGroup,
        String taskComment,
        String itemComment
    ) { }
}
